(ns com.biffweb.impl.time)

(def rfc3339 "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

(defn parse-format-date [date in-format out-format]
  (cond->> date
    in-format (.parse (new java.text.SimpleDateFormat in-format))
    out-format (.format (new java.text.SimpleDateFormat out-format))))

(defn parse-date
  ([date]
   (parse-date date rfc3339))
  ([date in-format]
   (parse-format-date date in-format nil)))

(defn format-date
  ([date]
   (format-date date rfc3339))
  ([date out-format]
   (parse-format-date date nil out-format)))

(defn crop-date [d fmt]
  (-> d
      (format-date fmt)
      (parse-date fmt)))

(defn crop-day [t]
  (crop-date t "yyyy-MM-dd"))

(defn- expand-now [x]
  (if (= x :now)
    (java.util.Date.)
    x))

(defn seconds-between [t1 t2]
  (quot (- (inst-ms (expand-now t2)) (inst-ms (expand-now t1))) 1000))

(defn duration [x unit]
  (case unit
    :seconds x
    :minutes (* x 60)
    :hours (* x 60 60)
    :days (* x 60 60 24)
    :weeks (* x 60 60 24 7)))

(defn elapsed? [t1 t2 x unit]
  (< (duration x unit)
     (seconds-between t1 t2)))

(defn between-hours? [t h1 h2]
  (let [hours (/ (mod (quot (inst-ms t) (* 1000 60))
                      (* 60 24))
                 60.0)]
    (if (< h1 h2)
      (<= h1 hours h2)
      (or (<= h1 hours)
          (<= hours h2)))))

(defn add-seconds [date seconds]
  (java.util.Date/from (.plusSeconds (.toInstant date) seconds)))

(defn now []
  (java.util.Date.))
