(defproject pazustep/lein-version-suffix "0.1.1"
  :author "Marcus Brito <marcus@bri.to>"
  :description "Leiningen plugin to add a version suffix to files"
  :url "http://github.com/pazustep/lein-version-suffix"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true
  :dependencies [[commons-codec "1.10"]]
  :profiles {
    :dev {
      :dependencies [[org.clojure/clojure "1.6.0"]]}})
