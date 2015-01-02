(ns pazustep.version-suffix
  (:require [clojure.java.io :refer [resource reader]]
            [clojure.edn :as edn])
  (:import (java.io PushbackReader)))

(defn read-config []
  (if-let [url (resource "assets.edn")]
    (with-open [reader (PushbackReader. (reader url))]
      (edn/read reader))))

(defn versioned-file-path
  ([path]
    (versioned-file-path (read-config) path))
  ([config path]
    (if config (get config path path) path)))
