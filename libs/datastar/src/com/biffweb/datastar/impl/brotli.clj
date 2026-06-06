(ns com.biffweb.datastar.impl.brotli
  (:require [clojure.java.io :as io])
  (:import
   (com.aayushatharva.brotli4j Brotli4jLoader)
   (com.aayushatharva.brotli4j.decoder BrotliInputStream Decoder)
   (com.aayushatharva.brotli4j.encoder BrotliOutputStream Encoder$Mode Encoder$Parameters)
   (java.io ByteArrayOutputStream IOException)))

#_:clj-kondo/ignore
(defonce ^:private ensure-brotli
  (Brotli4jLoader/ensureAvailability))

(defn- encoder-params [{:keys [quality window-size]}]
  (doto (Encoder$Parameters.)
    (.setMode Encoder$Mode/TEXT)
    (.setWindow (or window-size 18))
    (.setQuality (or quality 5))))

(defn byte-array-out-stream ^ByteArrayOutputStream []
  (ByteArrayOutputStream.))

(defn compress-out-stream ^BrotliOutputStream
  [^ByteArrayOutputStream out-stream & {:as opts}]
  (let [buffer-size 16384] ; copied from Hyperlith
    (BrotliOutputStream. out-stream (encoder-params opts) buffer-size)))

(defn compress-stream [^ByteArrayOutputStream out ^BrotliOutputStream br chunk]
  (doto br
    (.write (.getBytes ^String chunk "UTF-8"))
    (.flush))
  (let [result (.toByteArray out)]
    (.reset out)
    result))

(defn decompress [data]
  (let [decompressed (Decoder/decompress data)]
    (String. (.getDecompressedData decompressed) "UTF-8")))

(defn decompress-stream [data]
  (with-open [in  (-> data io/input-stream BrotliInputStream.)
              out (ByteArrayOutputStream.)]
    (.enableEagerOutput in)
    (try
      (loop [read (.read in)]
        (when (<= 0 read)
          (.write out read)
          (recur (.read in))))
      (catch IOException _))
    (str out)))
