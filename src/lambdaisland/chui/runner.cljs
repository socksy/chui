(ns lambdaisland.chui.runner
  (:require [cljs.test :as t]
            [clojure.string :as str]
            [kitchen-async.promise :as p]
            [lambdaisland.chui.interceptor :as intor]
            [lambdaisland.chui.test-data :as test-data]
            [lambdaisland.glogi :as log]))

(defonce test-runs (atom []))

(defn current-run []
  (last @test-runs))

(defn update-run [f & args]
  (swap! test-runs
         (fn [runs]
           (apply update runs (dec (count runs)) f args))))

(defn update-run-ns [f & args]
  (update-run
   (fn [run]
     (apply update-in run [:nss (dec (count (:nss run)))] f args))))

(defn update-run-var [f & args]
  (update-run-ns
   (fn [ns]
     (apply update-in ns [:vars (dec (count (:vars ns)))] f args))))

(defn get-and-clear-report-counters []
  (let [counters (:report-counters t/*current-env*)]
    (t/update-current-env! [:report-counters] (constantly {:test 0 :pass 0 :fail 0 :error 0}))
    counters))

(defn new-test-run! [m]
  (dec (count (swap! test-runs conj m))))

(defn cljs-test-intor
  "Turn a function which may return a cljs.test IAsyncTest into a promise-based interceptor.

  An IAsyncTest is a special type of object which is callable. It takes a single
  argument: a continuation callback, which is a zero-arity function. In effect
  it is like a promise which does not yield a value, but simply signals that
  some process has completed.

  IAsyncTest values are created using the `cljs.test/async` macro, which may be
  used in tests (deftest) and fixtures to implement asynchrony. "
  [f]
  {:name ::cljs-test-intor
   :enter (fn [ctx]
            (let [result (f)]
              (if (t/async? result)
                (p/promise [resolve]
                  (result
                   (fn []
                     (resolve ctx))))
                ctx)))})

(defn report-intor
  "Interceptor which calls cljs.test/report"
  [m]
  {:name (:type m)
   :enter (fn [ctx] (t/report m) ctx)})

(defn var-intors
  "Sequence of interceptors which handle a single test var."
  [test]
  (let [the-var (:var test)
        test-fn (:test (meta the-var))]
    [(report-intor {:type :begin-test-var :var the-var})
     {:name :begint-var-update-env
      :enter (fn [ctx]
               (t/update-current-env! [:testing-vars] conj the-var)
               (t/update-current-env! [:report-counters :test] inc)
               (update-run-ns update :vars conj (assoc test
                                                       :assertions []
                                                       :done? false))
               ctx)}
     (cljs-test-intor test-fn)
     {:name :end-var-update-env
      :enter (fn [ctx]
               (t/update-current-env! [:testing-vars] rest)
               (update-run-var assoc :done? true)
               ctx)}
     (report-intor {:type :end-test-var :var the-var})]))

(defn ns-intors
  "Sequence of interceptors which handle a single namespace, including
  once-fixtures and each-fixtures."
  [ns {:keys [tests each-fixtures once-fixtures] :as ns-data}]
  (concat
   [(report-intor {:type :begin-test-ns :ns ns})
    {:name :begin-ns-update-run
     :enter (fn [ctx]
              (update-run update :nss conj {:ns ns
                                            :done? false
                                            :vars []})
              ctx)}]
   (keep (comp cljs-test-intor :before) once-fixtures)
   (->> tests
        (sort-by (comp :line :meta))
        (map var-intors)
        (mapcat (fn [var-intors]
                  (concat
                   (keep (comp cljs-test-intor :before) each-fixtures)
                   var-intors
                   (reverse (keep (comp cljs-test-intor :after) each-fixtures))))))
   (reverse (keep (comp cljs-test-intor :after) once-fixtures))
   {:name :end-ns-update-run
    :enter (fn [ctx]
             (update-run update :nss assoc :done? true)
             ctx)}
   [(report-intor {:type :end-test-ns :ns ns})]))

(def log-error-intor
  {:name ::log-error
   :error (fn [ctx error]
            (let [data (ex-data error)]
              (log/log "lambdaisland.chui.runner" :error (dissoc data :exception) (:exception data))))})

#_
;; for debugging / visualizing progress
(defn slowdown-intor [ms]
  {:name ::slowdown
   :enter (fn [ctx]
            (p/promise [resolve]
              (js/setTimeout (fn []
                               (resolve ctx))
                             ms)))})

(defn run-tests [tests]
  (let [terminate? (atom false)]
    (new-test-run! {:terminate! #(reset! terminate? true)
                    :nss []
                    :ctx {}
                    :done? false
                    :start (js/Date.)})
    (set! t/*current-env* (t/empty-env ::default))
    (p/let [ctx (-> {::intor/terminate? terminate?
                     ::intor/on-context #(update-run assoc :ctx %)}
                    (intor/enqueue [log-error-intor])
                    (intor/enqueue (mapcat #(apply ns-intors %) tests))
                    intor/execute)]
      (update-run assoc
                  :ctx ctx
                  :done? true))))

;; cljs.test's version of this is utterly broken. This version is not great but
;; at least it kind of works in both Firefox and Chrome. To do this properly
;; we'll have to use something like stacktrace.js
(defn file-and-line []
  (let [frame (-> (js/Error.)
                  .-stack
                  (str/split #"\n")
                  (->> (drop-while #(not (str/includes? % "do_report"))))
                  (nth 1)
                  (str/split #":"))
        line-col (drop (- (count frame) 2) frame)
        file (str/join ":" (take (- (count frame) 2) frame))]
    {:file file
     :line (js/parseInt (re-find #"\d+" (first line-col)) 10)
     :column (js/parseInt (re-find #"\d+" (second line-col)) 10)}))

(defmethod t/report [::default :fail] [m]
  (update-run-var update :assertions conj (merge m (file-and-line))))

(defmethod t/report [::default :error] [m]
  (update-run-var update :assertions conj m))

(defmethod t/report [::default :pass] [m]
  (update-run-var update :assertions conj m))

(run-tests @test-data/test-ns-data)

(comment
  (defn legacy-reporter [reporter]
    (fn [m]
      ((get-method cljs-test-report [reporter (:type m)]) m)))

  (defn report [m]
    (doseq [f (:reporters (t/get-current-env))]
      (f m))))
