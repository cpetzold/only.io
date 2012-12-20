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
;(repl/connect "http://localhost:9000/repl")

;; constants
(def tweet-length 140)
(def google-auto "http://clients5.google.com/complete/search?hl=en&client=chrome&q=")

;; helpers
(def key-map
  {13 :enter
   9 :tab
   32 :space})

(def auto-map
  {:tweet "woot"})

;; state
(def initial-state
  {:mode :search
   :input ""
   :completes []})

(def state (atom initial-state))

(defn starts-with? [s p]
  (= 0 (.indexOf s p)))

(defn res-map [res]
  (walk/keywordize-keys (js->clj res)))

(defn google-format-completes [res]
  (let [strings (nth res 1)
        types (get-in res [4 :google:suggesttype])
        relevance (get-in res [4 :google:suggestrelevance])]
    (into [] (for [i (range 0 (count strings))]
      {:string (nth strings i)
       :type (nth types i)
       :rel (nth relevance i)}))))

(defn google-auto-update! [input res]
  (when (= input (:input @state))
    (swap! state assoc :completes (google-format-completes (res-map res)))))

(defn autocomplete [input]
  ($/ajax (str google-auto input)
          {:dataType "jsonp" :success (partial google-auto-update! input)}))

(add-dom-watch :tweet-watch [s new]
               (let [{:keys [value]} (second new)]
                 (when (< (count value) (inc tweet-length))
                   {:input value})))

(add-dom-watch :search-watch [s new]
               (let [{:keys [value]} (second new)
                     patch {:input value}
                     first-complete (get-in s [:completes 0 :string])]
                 (if (empty? value)
                   (assoc patch :completes [])
                   (do (autocomplete value) patch))))

(defn prevent-default [event]
  (.preventDefault (:e event)))

(defn search-submit [event]
  (prevent-default event)
  (set! js/window.location
        (str "//www.google.com/search?q=" (.-value (:target event)))))

(defn first-result [completes input]
  (if-let [complete (first
                     (drop-while
                      #(not (starts-with? (:string %) input))
                      completes))]
    (:string complete) ""))

(defn complete-suggestion [event]
  (prevent-default event)
  (let [s @state
        input (:input s)
        completes (:completes s)
        suggestion (first-result completes input)]
    (when-not (empty? suggestion)
      (let [new (swap! state assoc :input suggestion)]
        (autocomplete (:input new))))))


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

(def mode-map
  {:search
   {:placeholder "Search..."
    :input-watch :search-watch
    :tail-value (fn [{:keys [input completes]} s] (first-result completes input))
    :key-handle search}
   :tweet
   {:placeholder "Tweet..."
    :input-watch :tweet-watch
    :tail-value (fn [{:keys [input]} s] (if (empty? input) ""
                                            (str input (- tweet-length (count input)))))}})

(defn handle-keydown [e]
  (when-let [key-handle (:key-handle (mode-map (:mode @state)))]
    (key-handle (event-info e))))

(defn auto-result [query complete]
  (let [complete-str (:string complete)]
    (if (starts-with? complete-str query)
      [:div.complete [:strong query]
       (str/replace-first complete-str (re-pattern query) "")]
      [:div.complete complete-str])))

(defn other-mode [m]
  (if (= m :tweet) :search :tweet))

(defn toggle-mode []
  (let [curr-mode (:mode @state)
        new-mode (other-mode curr-mode)]
    (swap! state assoc :mode new-mode)
    (.focus (dom/get-element :input))))

(defn handle-click [e]
  (let [event (event-info e)]
    (when (= (:target-id event) "toggle-mode")
      (toggle-mode))))

(defn render [s]
  (let [{:keys [input completes mode]} s
        params (mode-map mode)]
    [:div#container
     [:button#toggle-mode (str "Switch to " (name (other-mode mode)) " mode")]
     [:div#input-wrapper
      [:textarea#input {:watch (:input-watch params)
                        :value input
                        :autofocus true
                        :placeholder (:placeholder params)}]
      [:textarea#tail {:value ((:tail-value params) s)
                       :disabled true}]]
     [:div#results
      {:class (when (or (= mode :tweet) (empty? completes)) "hide")}
      (map #(auto-result input %) completes)]]))

(fui/launch-app state render)

(event/listen js/document.body :keydown handle-keydown)
(event/listen js/document.body :click handle-click)