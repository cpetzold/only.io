(ns onlyio.core
  (:use-macros [webfui.framework.macros :only [add-dom-watch]])
  (:require [webfui.framework :as fui]
            [clojure.browser.dom :as dom]
            [clojure.browser.repl :as repl]
            [clojure.walk :as walk]
            [jayq.core :as $]))

(repl/connect "http://localhost:9000/repl")

(def wordnik-base "http://api.wordnik.com//v4/words.json/search/")

(def google-auto "http://suggestqueries.google.com/complete/search?client=chrome&q=")

(defn starts-with? [s p]
  (= 0 (.indexOf s p)))

(defn res-map [res]
  (walk/keywordize-keys (js->clj res)))

(defn autocomplete [q cb]
  ($/ajax (str google-auto q)
          {:dataType "jsonp"
           :success (fn [res]
                      (cb (when-let [words (nth res 1)]
                          (nth words 1)
                            (first
                             (drop-while
                              #(not (starts-with? % q))
                              words)))))}))

(defn get-word [q cb]
  ($/ajax (str wordnik-base q ".*?allowRegex=true&api_key=997ea3a64b190c6d8f0040df7b6003393e51bbd224bc5ec8d")
          {:dataType "jsonp"
           :success (fn [res]
                      (cb
                       (when-let [word (get-in (res-map res) [:searchResults 1 :word])]
                         word)))}))

(def tweet-length 140)

(def initial-state
  {:tweet ""
   :search {:query "" :result ""}})

(def state (atom initial-state))

(add-dom-watch :tweet-watch [s new]
               (let [{:keys [value]} (second new)]
                 (when (< (count value) (inc tweet-length))
                   {:tweet value})))

(add-dom-watch :search-watch [s new]
               (let [{:keys [value]} (second new)
                     patch {:search {:query value}}
                     query (-> s :search :query)
                     result (-> s :search :result)]
                 (if (and result
                          (= 0 (.indexOf result value))
                          (< query value))
                   patch
                   (do
                     (when-not (empty? value)
                       (autocomplete value
                                 (fn [word]
                                   (when (= value (-> @state :search :query))
                                     (swap! state assoc-in [:search :result] word)))))
                     (assoc-in patch [:search :result] value)))))
                     
(defn render [s]
  (let [{:keys [tweet search]} s]
    [:div
     [:div#tweet
      [:input#tweet-in {:watch :tweet-watch
                        :value tweet
                        :placeholder "Tweet..."}]
      [:input#tweet-count {:value (if (empty? tweet) ""
                                      (str tweet (- tweet-length (count tweet))))
                           :disabled true}]]
     [:div#search
      [:input#search-query {:watch :search-watch
                            :value (:query search)
                            :autofocus true
                            :placeholder "Search..."}]
      [:input#search-res {:value (:result search)
                          :disabled true}]]]))

(fui/launch-app state render)