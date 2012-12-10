(ns jnput.core
  (:use-macros [webfui.framework.macros :only [add-dom-watch]])
  (:require [webfui.framework :as fui]
            [clojure.browser.dom :as dom]))

(def tweet-length 140)

(def initial-state {:input "hello world"})

(def state (atom initial-state))

(add-dom-watch :in-watch [state new]
               (dom/log state)
               (let [{:keys [value]} (second new)]
                 (when (< (count value) (inc tweet-length))
                   {:input value})))

(defn render [state]
  (let [{:keys [input]} state]
    [:div
     [:input#in {:watch :in-watch
                 :value input
                 :autofocus true}]
     [:input#auto {:value (str input (- tweet-length (count input)))
                   :disabled true}]]))

(fui/launch-app state render)