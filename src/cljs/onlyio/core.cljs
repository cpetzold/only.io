(ns onlyio.core
  (:use-macros [webfui.framework.macros :only [add-dom-watch]])
  (:require [webfui.framework :as fui]
            [clojure.string :as str]
            [clojure.browser.dom :as dom]
            [clojure.browser.event :as event]
            [clojure.browser.repl :as repl]
            [clojure.walk :as walk]
            [jayq.core :as $]))

;; repl
(repl/connect "http://localhost:9000/repl")

;; constants
(def tweet-length 140)
(def wordnik-base "http://api.wordnik.com/v4/words.json/search/")
(def google-auto "http://clients5.google.com/complete/search?hl=en&client=chrome&q=")

;; helpers
(def key-map
  {13 :enter
   9 :tab
   32 :space})

;; state
(def initial-state
  {:key-handle nil
   :tweet ""
   :search {:query "" :completes []}})

(def state (atom initial-state))

(defn starts-with? [s p]
  (= 0 (.indexOf s p)))

(defn res-map [res]
  (walk/keywordize-keys (js->clj res)))

(defn format-completes [res]
  (let [strings (nth res 1)
        types (get-in res [4 :google:suggesttype])
        relevance (get-in res [4 :google:suggestrelevance])]
    (into [] (for [i (range 0 (count strings))]
      {:string (nth strings i)
       :type (nth types i)
       :rel (nth relevance i)}))))

(defn autocomplete [q]
  ($/ajax (str google-auto q)
          {:dataType "jsonp"
           :success (fn [res]
                      (when (= q (-> @state :search :query))
                        (swap! state assoc-in [:search :completes]
                               (format-completes (res-map res)))))}))

(defn get-word [q cb]
  ($/ajax (str wordnik-base q ".*?allowRegex=true&api_key=997ea3a64b190c6d8f0040df7b6003393e51bbd224bc5ec8d")
          {:dataType "jsonp"
           :success (fn [res]
                      (cb
                       (when-let [word (get-in (res-map res) [:searchResults 1 :word])]
                         word)))}))

(add-dom-watch :tweet-watch [s new]
               (let [{:keys [value]} (second new)]
                 (when (< (count value) (inc tweet-length))
                   {:tweet value})))

(add-dom-watch :input-watch [s new]
               (let [{:keys [value]} (second new)
                     patch {:search {:query value}}
                     search (:search s)
                     first-complete (get-in search [:completes 0 :string])]
                 (if (empty? value)
                   (assoc-in patch [:search :completes] [])
                   (do (autocomplete value) patch))))

(defn prevent-default [event]
  (.preventDefault (:e event)))

(defn search-submit [event]
  (prevent-default event)
  (set! js/window.location
        (str "//www.google.com/search?q=" (.-value (:target event)))))

(defn first-result [search]
  (if-let [complete (first
                     (drop-while
                      #(not (starts-with? (:string %) (:query search)))
                      (:completes search)))]
    (:string complete) ""))

(defn complete-suggestion [event]
  (prevent-default event)
  (let [search (:search @state)
        query (:query search)
        suggestion (first-result search)]
    (when-not (empty? suggestion)
      (let [s (swap! state assoc-in [:search :query] suggestion)]
        (autocomplete (-> s :search :query))))))


(defn event-info [e]
  (let [target (.-target e)
        code (.-keyCode e)]
    {:key-code code
     :key (key-map code)
     :target target
     :target-id (.getAttribute target "id")
     :e e}))

(defn search [event]
  (let [key (:key event)
        id (:target-id event)]
    (cond
     (and (= key :enter) (= id "input")) (search-submit event)
     (and (= key :tab) (= id "input")) (complete-suggestion event))))

(defn handle-keydown [e]
  ((:key-handle @state) (event-info e)))

(defn auto-result [query complete]
  (let [complete-str (:string complete)]
    (if (starts-with? complete-str query)
      [:div.complete [:strong query]
       (str/replace-first complete-str (re-pattern query) "")]
      [:div.complete complete-str])))

(defn render [s]
  (let [{:keys [search]} s]
    [:div#container
     [:div#input-wrapper
      [:textarea#input {:watch :input-watch
                        :value (:query search)
                        :autofocus true
                        :placeholder "Search..."}]
      [:textarea#input-back {:value (first-result search)
                             :disabled true}]]
     [:div#results
      {:class (when (empty? (:completes search)) "hide")}
      (map #(auto-result (:query search) %) (:completes search))]]))

(swap! state assoc :key-handle search)
(fui/launch-app state render)

(event/listen js/document.body :keydown handle-keydown)