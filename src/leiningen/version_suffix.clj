(ns leiningen.version-suffix
  (:require
    [clojure.java.io :refer [reader]]
    [leiningen.core.main :refer [abort]])
  (:import
    (java.io File FileInputStream FileOutputStream)
    (java.util.zip GZIPOutputStream)
    (org.apache.commons.codec.digest DigestUtils)
    (org.apache.commons.codec.binary Base32)))

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

(defn validate-files-entry [root entry]
  (let [path (str root entry)
        file (File. path)]
    (if-not (.exists file)
      (abort (format "File %s does not exist." path))
      (if-not (.isFile file)
        (abort (format "File %s is not a regular file." path))))))

(defn validate-files-list [config]
  (let [root (:root config)
        assets (:files config)]
    (if (nil? assets)
      (abort ":files key under :version-suffix is required.")
      (if-not (vector? assets)
        (abort ":assets key under :version-suffix must be a vector.")
        (dorun (map (partial validate-files-entry root) assets))))))

(defn validate-config [config]
  (if-not (map? config)
    (abort ":version-suffix key must be a map.")
    (do
      (validate-root config)
      (validate-files-list config))))

(defn output-dir [project]
      (or (get-in project [:version-suffix :output-to])
          (first (:resource-paths project))
          (first (:source-paths project))))

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
  (let [gzip-file (str (.getPath file) ".gz")]
    (with-open [input (FileInputStream. file)
                output (GZIPOutputStream. (FileOutputStream. gzip-file))]
      (let [buffer (byte-array 8192)
            fill-buffer (fn [] (.read input buffer))]
        (loop [read-count (fill-buffer)]
          (when (pos? read-count)
            (.write output buffer 0 read-count)
            (recur (fill-buffer))))))))

(defn rename-file [root gzip? path]
  (let [file (File. root path)
        version (digest-file file)
        target (target-file file version)]
    (.renameTo file target)
    (if gzip? (gzip-file target))
    [path (subs (.getPath target) (.length root))]))

(defn version-files [project]
  (let [config (:version-suffix project)
        runtime-data (into {} (map (partial rename-file (:root config) (:gzip config)) (:files config)))
        output-to (File. (output-dir project) "assets.edn")]
    (spit output-to runtime-data)
    runtime-data))

(defn version-suffix
  "I don't do a lot."
  [project & args]
  (let [config (:version-suffix project)]
    (when-not (nil? config)
      (validate-config config)
      (version-files project))))
