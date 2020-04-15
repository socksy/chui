(ns lambdaisland.chui.styles
  (:require [garden.core :as garden]
            [garden.stylesheet :as stylesheet]
            [garden.selectors :as selectors]))

(def washed-red "#FFDFDF")
(def washed-green "#E8FDF5")

(def styles
  [[:main
    {:margin "5vh 10vw"
     :font-family "sans-serif"}]

   [:.spinner
    {:display "inline-block"
     :width "1em"
     :height "1em"}
    [:&:after
     {:content "\" \""
      :display "block"
      :width "0.8em"
      :height "0.8em"
      :border-radius "50%"
      :border "6px solid #555"
      :border-color "#555 transparent #555 transparent"
      :animation "spinner 1.2s linear infinite"}]]

   (stylesheet/at-keyframes
    :spinner
    ["0%" {:transform "rotate(0deg)"}]
    ["100%" {:transform "rotate(360deg)"}])

   [:.run.fail [:h1 {:background-color washed-red}]]
   [:.run.error [:h1 {:background-color washed-red}]]
   [:.run.pass [:h1 {:background-color washed-green}]]
   [:.ns.error {:background-color washed-red}]
   [:.ns.fail {:background-color washed-red}]
   [:.ns.pass {:background-color washed-green}]
   [:.ns
    [:h2 {:border-bottom "1px solid #666"}]
    [:.filename {:float "right"
                 :color "#777"}]]])

(def style2
  [[:* {:box-sizing "border-box"}]
   [:html {:color "#333"
           :font-family "sans-serif"
           :height "100vh"}]
   [:body {:margin 0
           :height "100%"}]
   [:#app {:height "100%"}
    [:> [:div {:height "100%"
               :display :grid
               :grid-template-rows "auto 1fr"
               :grid-gap ".3rem"
               :padding ".3rem"}]]]
   [:header {:background-color "#6b6bff"
             :color "#7cdfff"
             :padding ".3rem"
             :border-radius ".1rem"
             :display :flex
             :justify-content :space-between}]
   [:.interface-controls {:display :flex}]
   [:ul {:padding ".2rem"
         :list-style :none
         :text-decoration :none
         :line-height 1.5}]
   [:li [:a {:text-decoration :none}]]
   [:li:hover
    :li:focus-within
    :li:focus
    :li:active
    :li:hover
    {:border "1px solid yellow"
     :background-color "#ffffc4"
     :padding ".3rem"
     :text-decoration :none}
    [:a {:text-decoration :none}]]
   [:main
    {:display :flex
     :width "100%"
     :overflow-x "auto"
     :scroll-snap-type "x mandatory"
     :background-color :initial
     :padding-left "20%"}
    [:>section {:flex-shrink 0
                :width "calc(100vw / 3)"
                :scroll-snap-align :center
                :scrollbar-width :none
                :display :flex
                :flex-direction :column
                :padding ".5rem"}
     [:&:focus-within {:background-color :lightyellow}]]]
   [:.namespaces {:background-color :inherit}]
   [:.fieldset {:border "1px solid black"
                :margin-top ".3rem"
                :margin-bottom ".3rem"}]
   [(selectors/attr= "type" "search")
    {:padding ".5rem"
     :border "none"
     :width "100%"
     :font-size "1.125rem"}]
   [:.history { :background-color :inherit}]
   [:.test-info { :background-color :initial}]
   [:.namespaces [:+ul {:padding-left "1.5rem"
                        :line-height "1.7rem"}]]
   [:.name {:color :white
            :text-decoration :none
            :padding-right ".3rem"}]
   [:.toggle {:position :absolute
              :left "-100vw"}]
   [:.namespace-selector {:display :flex
                          :flex-direction :column}]
   [:.active {:font-weight :bold}]])

(defmacro inline []
  (garden/css {:pretty-print? false} style2))
