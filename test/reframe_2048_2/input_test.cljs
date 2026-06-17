(ns reframe-2048-2.input-test
  "Touch/swipe adapter tests. `swipe->key` is the pure mapper from a
   touch displacement to an arrow-key string; the DOM listeners that feed
   it (board.cljs) are thin enough to exercise by hand on a device."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [reframe-2048-2.input :as input]))

(def ^:private thr input/swipe-threshold-px)

(deftest swipe->key-dominant-axis
  (testing "clear horizontal swipes map to left/right"
    (is (= "ArrowRight" (input/swipe->key 80 0)))
    (is (= "ArrowLeft"  (input/swipe->key -80 0)))
    ;; horizontal dominates a smaller vertical wobble
    (is (= "ArrowRight" (input/swipe->key 80 10)))
    (is (= "ArrowLeft"  (input/swipe->key -80 -10))))
  (testing "clear vertical swipes map to up/down"
    (is (= "ArrowDown" (input/swipe->key 0 80)))
    (is (= "ArrowUp"   (input/swipe->key 0 -80)))
    (is (= "ArrowDown" (input/swipe->key 10 80)))
    (is (= "ArrowUp"   (input/swipe->key -10 -80)))))

(deftest swipe->key-below-threshold
  (testing "a tap / tiny drag below threshold on both axes is nil"
    (is (nil? (input/swipe->key 0 0)))
    (is (nil? (input/swipe->key (dec thr) (dec thr))))
    (is (nil? (input/swipe->key (- (dec thr)) (- (dec thr)))))))

(deftest swipe->key-threshold-boundary
  (testing "exactly at threshold counts as a swipe"
    (is (= "ArrowRight" (input/swipe->key thr 0)))
    (is (= "ArrowDown"  (input/swipe->key 0 thr))))
  (testing "an exact diagonal tie resolves to the horizontal axis"
    (is (= "ArrowRight" (input/swipe->key thr thr)))
    (is (= "ArrowLeft"  (input/swipe->key (- thr) thr)))))
