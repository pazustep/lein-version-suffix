(ns leiningen.version-suffix
  (:require
    [clojure.java.io :refer [reader]]
    [leiningen.core.main :refer [abort]])
  (:import
    (java.io File FileInputStream FileOutputStream)
    (java.util.zip GZIPOutputStream)
    (org.apache.commons.codec.digest DigestUtils)
    (org.apache.commons.codec.binary Base32)
    (java.util.regex Pattern)))

(def ^:dynamic *verbose* true)

(defn message [& args]
  (if *verbose* (apply println args)))

(defn validate-root [config]
  (let [root (:root config)]
    (if (nil? root)
      (abort ":root key under :version-suffix is required.")
      (if-not (string? root)
        (abort ":root key under :version-suffix must be a string.")
        (let [file (File. root)]
          (if-not (.exists file)
            (abort (format "Version suffix root %s does not exist." root))
            (if-not (.isDirectory file)
              (abort (format "Version suffix root %s is not a directory." root)))))))))

(defn validate-files-entry [root ignore-missing? entry]
  (let [path (str root entry)
        file (File. path)]
    (if-not (.exists file)
      (if-not ignore-missing?
        (abort (format "File %s does not exist. Set {:ignore-missing true} if this is expected." path)))
      (if-not (.isFile file)
        (abort (format "File %s is not a file." path))))))

(defn validate-files-list [config]
  (let [root (:root config)
        assets (:files config)
        ignore-missing? (:ignore-missing config)]
    (if (nil? assets)
      (abort ":files key under :version-suffix is required.")
      (if-not (vector? assets)
        (abort ":assets key under :version-suffix must be a vector.")
        (dorun (map (partial validate-files-entry root ignore-missing?) assets))))))

(defn validate-config [config]
  (if-not (map? config)
    (abort ":version-suffix key must be a map.")
    (do
      (validate-root config)
      (validate-files-list config))))

(defn path-exists [path]
  (.exists (File. path)))

(defn output-dir [project]
  (or (get-in project [:version-suffix :output-to])
      (first (filter path-exists (:resource-paths project)))
      (first (filter path-exists (:source-paths project)))))

(defn split-ext [name]
  (let [dotpos (.indexOf name ".")]
    (if (neg? dotpos)
        [name nil]
        [(subs name 0 dotpos) (subs name dotpos)])))

(defn name-with-version [name version]
  (let [[base ext] (split-ext name)]
    (str base "-" version ext)))

(defn target-file [^File original ^String version]
  (let [parent (.getParent original)
        name  (.getName original)
        output-name (name-with-version name version)]
    (File. parent output-name)))

(defn digest-file [^File file]
  (with-open [stream (FileInputStream. file)]
    (let [bytes (DigestUtils/sha1 stream)]
      (.toLowerCase (.encodeToString (Base32.) bytes)))))

(defn gzip-file [file]
  (let [gzip-file (File. (str (.getPath file) ".gz"))]
    (with-open [input (FileInputStream. file)
                output (GZIPOutputStream. (FileOutputStream. gzip-file))]
      (let [buffer (byte-array 8192)
            fill-buffer (fn [] (.read input buffer))]
        (loop [read-count (fill-buffer)]
          (when (pos? read-count)
            (.write output buffer 0 read-count)
            (recur (fill-buffer))))))
    (.setLastModified gzip-file (.lastModified file))))

(defn versioned-file-filter [file]
  (let [[base ext] (split-ext (.getName file))
        pattern (re-pattern (str (Pattern/quote base)
                                 "-\\w{32}"
                                 (Pattern/quote ext)
                                 "(?:\\.gz)?"))]
    (reify
      java.io.FilenameFilter
      (accept [_this _dir name]
        (.matches (re-matcher pattern name))))))

(defn clean-old-versions [file]
  (let [parent (.getParentFile file)
        files (.listFiles parent (versioned-file-filter file))]
    (doseq [file files]
      (.delete file)
      (message "Removed stale versioned file" (.getPath file)))))

(defn rename-file [config path]
  (let [root (:root config)
        file (File. root path)]
    (if (.exists file)
      (let [version (digest-file file)
            target (target-file file version)]
        (if (:clean config) (clean-old-versions file))
        (.renameTo file target)
        (message "Versioned" (.getPath file) "->" (.getPath target))
        (if (:gzip config) (gzip-file target))
        [path (subs (.getPath target) (.length root))])
      (message "Ignored missing" (.getPath file)))))

(defn version-files [project]
  (let [config (:version-suffix project)
        runtime-data (into {} (map (partial rename-file config) (:files config)))
        output-to (File. (output-dir project) "assets.edn")]
    (spit output-to runtime-data)
    runtime-data))

(defn version-suffix
  "Add a version suffix to files."
  [project & args]
  (let [config (:version-suffix project)]
    (when-not (nil? config)
      (validate-config config)
      (binding [*verbose* (not (:quiet config))]
        (version-files project)))))
