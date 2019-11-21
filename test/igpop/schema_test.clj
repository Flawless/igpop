(ns igpop.schema-test
  (:require [igpop.schema :as sut]
            [clojure.test :refer :all]
            [matcho.core :as matcho]
            [clojure.java.io :as io]
            [igpop.loader :as loader]
            [cheshire.core :refer :all]))

(deftest test-schema-gen

  (def project-path (.getPath (io/resource "test-project")))

  (def project (loader/load-project project-path))

  (comment
    (sut/generate-json-schema project)

    (get-in project [:profiles :Task :basic :elements])

    (spit (io/file "/home/victor/Documents/Trash/test-schema.json") (generate-string (sut/generate-schema project) {:pretty true}))

    )

  (get-in project [:profiles :Task :basic :elements :input :elements :value])

  (testing "get concepts"
    (matcho/match
     ["male" "female"] (sut/get-concepts project :dict1))

    (matcho/match
     ["male" "female" "androgin" "unknonw"] (sut/get-concepts project :administrative-genders))

     (matcho/match
      ["draft" "active" "on-hold" "revoked" "completed" "entered-in-error" "unknown"] (sut/get-concepts project :careplan-status))))
