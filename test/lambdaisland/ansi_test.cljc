(ns lambdaisland.ansi-test
  (:require [clojure.test :as t :refer [deftest testing is are run-tests]]
            [lambdaisland.ansi :as ansi]
            #?(:cljs [doo.runner :refer-macros [doo-tests]])))

(deftest color-8-bit-test
  (are [x] x
    (= (ansi/color-8-bit 3)
       [[:rgb 205 205 0] false])

    (= (ansi/color-8-bit 12)
       [[:rgb 0 0 238] true])

    (= (ansi/color-8-bit 97)
       [[:rgb 0x87 0x5f 0xaf] false])

    (= (ansi/color-8-bit 232)
       [[:rgb 8 8 8] false])

    (= (ansi/color-8-bit 244)
       [[:rgb 128 128 128] false])

    (= (ansi/color-8-bit 255)
       [[:rgb 238 238 238] false])))

(deftest token-stream-test
  (is (= (ansi/token-stream (str "start of the string"
                                 "\033[31m this is red"
                                 "\033[45m magenta background"
                                 "\033[1m bold"
                                 "\033[32m green foreground"))
         ["start of the string"
          {:foreground [:rgb 205 0 0]}
          " this is red"
          {:background [:rgb 205 0 205]}
          " magenta background"
          {:bold true}
          " bold"
          {:foreground [:rgb 0 205 0]}
          " green foreground"]))

  (are [x] x
    (= (ansi/token-stream "\033[30;47m black on white")
       [{:foreground [:rgb 0 0 0], :background [:rgb 229 229 229]} " black on white"])

    (= (ansi/token-stream "\033[1;31m bright red]")
       [{:bold true, :foreground [:rgb 205 0 0]} " bright red]"])

    (= (ansi/token-stream "\033[39;49m reset to defaults]")
       [{:foreground nil, :background nil} " reset to defaults]"])

    (= (ansi/token-stream "\033[0m reset all")
       [{:foreground nil, :background nil, :bold nil} " reset all"])

    (= (ansi/token-stream "\033[91m bright red")
       [{:foreground [:rgb 255 0 0]} " bright red"])

    (= (ansi/token-stream "\033[m   raw-url-test")
       [{:foreground nil, :background nil, :bold nil} "   raw-url-test"])))

(deftest apply-props-test
  (is (= (sequence ansi/apply-props
                   (ansi/token-stream
                    (str "start of the string"
                         "\033[31m this is red"
                         "\033[45m magenta background"
                         "\033[1m bold"
                         "\033[39m reset foreground"
                         "\033[49m reset background"
                         "\033[32m green foreground"
                         "\033[0;38;2;99;88;77m reset + rgb color")))
         [[{} "start of the string"]
          [{:foreground [:rgb 205 0 0]} " this is red"]
          [{:foreground [:rgb 205 0 0], :background [:rgb 205 0 205]}
           " magenta background"]
          [{:foreground [:rgb 205 0 0], :background [:rgb 205 0 205], :bold true}
           " bold"]
          [{:background [:rgb 205 0 205], :bold true} " reset foreground"]
          [{:bold true} " reset background"]
          [{:bold true, :foreground [:rgb 0 205 0]} " green foreground"]
          [{:foreground [:rgb 99 88 77]} " reset + rgb color"]])))


(deftest text->hiccup-test
  (is (= (ansi/text->hiccup (str "start of the string"
                                 "\033[31m this is red"
                                 "\033[45m magenta background"
                                 "\033[1m bold"
                                 "\033[39m reset foreground"
                                 "\033[49m reset background"
                                 "\033[32m green foreground"
                                 "\033[0;38;2;99;88;77m reset + rgb color"))

         [[:span {} "start of the string"]
          [:span {:style {:color "rgb(205,0,0)"}} " this is red"]
          [:span
           {:style {:color "rgb(205,0,0)", :background-color "rgb(205,0,205)"}}
           " magenta background"]
          [:span
           {:style
            {:color "rgb(205,0,0)",
             :background-color "rgb(205,0,205)",
             :font-weight "bold"}}
           " bold"]
          [:span
           {:style {:background-color "rgb(205,0,205)", :font-weight "bold"}}
           " reset foreground"]
          [:span {:style {:font-weight "bold"}} " reset background"]
          [:span
           {:style {:color "rgb(0,205,0)", :font-weight "bold"}}
           " green foreground"]
          [:span {:style {:color "rgb(99,88,77)"}} " reset + rgb color"]])))

(deftest has-escape-char?-test
  (are [x y] (= x y)
    true  (ansi/has-escape-char? "\033[xxx")
    true  (ansi/has-escape-char? "xxx\033[xxx")
    true  (ansi/has-escape-char? "xxx\033[")
    true  (ansi/has-escape-char? "\033[")
    false (ansi/has-escape-char? "xxxx")
    false (ansi/has-escape-char? "x")
    false (ansi/has-escape-char? "")))

(deftest str-split-test
  (is (= (ansi/str-split "" ";")
         [""]))
  (is (= (ansi/str-split "foo" ";")
         ["foo"]))
  (is (= (ansi/str-split "foo;bar" ";")
         ["foo" "bar"]))
  (is (= (ansi/str-split "foo;bar;baz" ";")
         ["foo" "bar" "baz"]))
  (is (= (ansi/str-split "foo;;baz" ";")
         ["foo" "" "baz"] )))

(deftest str-scan-test
  (is (= (ansi/str-scan 0 "abc123" 97 122)
         3))

  (is (= (ansi/str-scan 0 "" 97 122)
         0))

  (is (= (ansi/str-scan 0 "AAA" 97 122)
         0))

  (is (= (ansi/str-scan 2 "AAA" 97 122)
         2))

  (is (= (ansi/str-scan 2 "AAabcAA" 97 122)
         5)))

(deftest next-csi-test
  (is (= (ansi/next-csi "aaa\033[0mbbb")
         ["aaa" "0m" "bbb"]))

  (is (= (ansi/next-csi "")
         nil))

  (is (= (ansi/next-csi "aaa\033[0mbbb\033[5;18;37m")
         ["aaa" "0m" "bbb[5;18;37m"]))

  (is (= (-> (ansi/next-csi "aaa\033[0mbbb\033[5;18;37m")
             last
             ansi/next-csi)
         ["bbb" "5;18;37m" ""])))

(deftest csi->attrs-test
  (is (= {:background [:rgb 205 0 205]}
         (ansi/csi->attrs "45m")))

  (is (= {:bold true}
         (ansi/csi->attrs "1m")))

  (is (= {:foreground nil}
         (ansi/csi->attrs "39m")))

  (is (= {:background nil}
         (ansi/csi->attrs "49m")))

  (is (= {:foreground [:rgb 0 205 0]}
         (ansi/csi->attrs "32m")))

  (is (= {:foreground [:rgb 99 88 77], :background nil, :bold nil}
         (ansi/csi->attrs "0;38;2;99;88;77m")))

  (is (= {:foreground [:rgb 0 0 0], :background [:rgb 229 229 229]}
         (ansi/csi->attrs "30;47m")))

  (is (= {:bold true, :foreground [:rgb 205 0 0]}
         (ansi/csi->attrs "1;31m")))

  (is (= {:foreground nil, :background nil}
         (ansi/csi->attrs "39;49m")))

  (is (= {:foreground [:rgb 255 0 0]}
         (ansi/csi->attrs "91m")))

  (is (= {:foreground [:rgb 205 0 0]}
         (ansi/csi->attrs "31m")))

  (is (= {:background [:rgb 205 0 205]}
         (ansi/csi->attrs "45m")))

  (is (= {:foreground nil, :background nil, :bold nil}
         (ansi/csi->attrs "0m")))

  (is (= {:foreground nil, :background nil, :bold nil}
         (ansi/csi->attrs "m"))))


#?(:cljs (doo-tests 'lambdaisland.ansi-test))

#_
(run-tests)
