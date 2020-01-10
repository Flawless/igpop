(ns igpop.structure-def-test
  (:require [igpop.structure-def :as sdef]
            [clojure.test :refer :all]
            [matcho.core :as matcho]
            [clojure.java.io :as io]
            [igpop.loader :as loader]
            [cheshire.core :refer :all]))

(deftest test-structure-gen

  (def profile
    {:Patient
     {:elements
      {:name {:type "HumanName"
              :required true
              :elements
              {:family {:type "string" :isCollection true :minItem 1 :maxItem 10}}}}}})

  (testing "cardinality: entity `required`"
    (matcho/match
     (sdef/to-sd profile)
     {:snapshot [{:path "Patient.name" :min 1}]}))

  (clojure.pprint/pprint (sdef/to-sd profile))

  (testing "cardinality: entity `disabled`"
    (matcho/match
     (sdef/to-sd {:Patient {:elements {:name {:disabled true :type "HumanName"}}}})
     {:snapshot [{:path "Patient.name" :max 0}]}))

  (testing "cardinality: collection `min` and `max`"
    (matcho/match
     (sdef/to-sd profile)
     {:snapshot [{} {:path "Patient.name.family" :min 1 :max 10}]}))

  (testing "`mustSupport`: default  value"
    (matcho/match
     (sdef/to-sd profile)
     {:snapshot [{:mustSupport true}]}))

  (testing "`mustSupport`: override value"
    (matcho/match
     (sdef/to-sd {:Patient {:elements {:name {:type "HumanName" :mustSupport false}}}})
     {:snapshot [{:mustSupport false}]}))

  (testing "path: Correct `path`(nesting) construction"
    (matcho/match
     (sdef/to-sd profile)
     {:snapshot [{:path "Patient.name"} {:path "Patient.name.family"}]}))

  (testing "constant: correct key definition"
    (matcho/match
     (sdef/to-sd {:Patient {:elements {:name {:type "HumanName" :constant "Rich"}}}})
     {:snapshot [{:fixedHumanName "Rich"}]}))

  (def observation-profile
    {:Observation
     {:elements
      {:name
       {:constraints
        {:us-core-8 {:expression "family.exists() or given.exists()"
                     :description "Patient.name.given or Patient.name.family  or both SHALL be present"}}}}}})

  (testing "constraint: default value for `severity`"
    (matcho/match
     (sdef/to-sd observation-profile)
     {:snapshot [{:constraint [{:severity "error"}]}]}))

  (testing "constraint: correct key value"
    (matcho/match
     (sdef/to-sd observation-profile)
     {:snapshot [{:constraint [{:key "us-core-8"}]}]}))

  (testing "constraint: description -> human field"
    (matcho/match
     (sdef/to-sd observation-profile)
     {:snapshot [{:constraint
                  [{:human "Patient.name.given or Patient.name.family  or both SHALL be present"}]}]}))

  (testing "constraint: expression -> human field"
    (matcho/match
     (sdef/to-sd observation-profile)
     {:snapshot [{:constraint [{:expression  "family.exists() or given.exists()"
                                :human  "Patient.name.given or Patient.name.family  or both SHALL be present"}]}]}))

  (def polymorphic-type
    {:Observation
     {:elements
      {:value {:required true
               :union ["string" "CodeableConcept" "Quantity"]
               :string {:required true}
               :CodeableConcept {:required true
                                 :valueset {:id "vs"}}}}}})

  (testing "polymorphic: created item with `[x]` path and discriminator"
    (matcho/match
     (sdef/to-sd polymorphic-type)
     {:snapshot
      [{:path "Observation.value[x]"
        :slicing {:discriminator {:type "type"}
                  :path "$this"
                  :ordered false
                  :rules "closed"} }]}))

  (testing "polymorphic: created item `[x]` with correct type"
    (matcho/match
     (sdef/to-sd polymorphic-type)
     {:snapshot [{:type [{:code "string"},{:code "CodeableConcept"},{:code "Quantity"}]}]}))

  (testing "polymorphic: create items for union-types"
    (matcho/match
     (sdef/to-sd polymorphic-type)
     {:snapshot [{} {:path "Observation.valueString"}
                    {:path "Observation.valueCodeableConcept"}]}))

  (testing "polymorphic: create items with valueset placed in binding"
    (matcho/match
     (sdef/to-sd polymorphic-type)
     {:snapshot [{} {} {:binding {:valueset {:id "vs"}}}]}))

  ;; --------------------------------------------------------------------------------------

  ;;Functional tests
  ;;differential generation

  (def props
    {:elements
     {:name {:type "HumanName"
             :required true
             :description "Hi"
             :elements
             {:family {:type "string" :isCollection true :minItem 1 :maxItem 10 }}
             :refers [{
                       :profile "basic"
                       :resourceType "Practitioner"}
                      {:resourceType "Organization"
                       :profile "basic"}
                      {
                       :profile "basic"
                       :resourceType "Patient"}]
             :valueset {:id "sample"
                        :strength "required"
                        :description "This is a valueset"}}
      :birthDate {:required true}
      :code {:constant "female"}
      :coding
      {:constant {:code "code-1"
                  :system "sys-1"}}
      :animal {:disable true}}})


  (testing "fixed values"
    (matcho/match
     (sdef/generate-differential :Patient "basic" props)
     {}))

  (clojure.pprint/pprint (sdef/generate-differential :Patient "basic" props))

  (clojure.pprint/pprint (sdef/elements-to-sd (:elements props)))

  ;;Unit tests

  (def plain-elements-in
    {:CarePlan.subject {}
     :CarePlan.text {}})

  (testing "elements-to-sd func"
    (matcho/match
     (sdef/elements-to-sd plain-elements-in)
                          [{:id "CarePlan.subject",
                            :path "CarePlan.subject"},
                           {:id "CarePlan.text",
                            :path "CarePlan.text"}]))

  (testing "cardinality required"
    (matcho/match
     (sdef/cardinality :required true)
     {:min 1}))

  (testing "cardinality disabled"
    (matcho/match
     (sdef/cardinality :disabled true)
     {:max 0}))

  (testing "cardinality minItems"
    (matcho/match
     (sdef/cardinality :minItems 5)
     {:min 5}))

  (testing "cardinality maxItems"
    (matcho/match
     (sdef/cardinality :maxItems 14)
     {:max 14}))

  (testing "mustSupport default"
    (matcho/match
     (sdef/mustSupport)
     {:mustSupport true}))

  (testing "mustSupport from profile"
    (matcho/match
     (sdef/mustSupport false)
     {:mustSupport false}))

  (def constraint-example
    '({:ele-1 {:description "All FHIR elements"}}
      {:ext-1 {:expression "Must have either" :severity "init"}}))

  (testing "fhirpath rules"
    (matcho/match
     (sdef/fhirpath-rule :constraints constraint-example)
     {:constraints [{:key "ele-1", :severity "error", :human "All FHIR elements"}
      {:key "ext-1", :severity "init", :expression "Must have either"}]}))

  (testing "refers"
    (matcho/match
     (sdef/refers [{:profile "basic"
                    :resourceType "Practitioner"}
                   {:resourceType "Organization"
                    :profile "basic"}
                   {:profile "basic"
                    :resourceType "Patient"}])
     {:type
      [{:code "Reference",
        :targetProfile
        ["https://healthsamurai.github.io/igpop/profiles/Practitioner/basic.html"]}
       {:code "Reference",
        :targetProfile
        ["https://healthsamurai.github.io/igpop/profiles/Organization/basic.html"]}
       {:code "Reference",
        :targetProfile
        ["https://healthsamurai.github.io/igpop/profiles/Patient/basic.html"]}]}))

  (testing "valueset"
    (matcho/match
     (sdef/valueset {:id "sample" :description "test"})
     {:binding {:valueSet "https://healthsamurai.github.io/igpop/valuesets/sample.html" :description "test" :strength "extensible"}}))

  (testing "description"
    (matcho/match
     (sdef/description "test")
     {:short "test"}))

  (def project-path (.getPath (io/resource "test-project")))

  (def project (loader/load-project project-path))

  (comment

    (println project)

    (spit (io/file (str (System/getProperty "user.dir") "/show-project.json")) (generate-string project {:pretty true}))

    (println (sdef/generate-structure project))

    (get-in project [:profiles :Patient :basic :elements :name :elements :given])

    (spit (io/file (str (System/getProperty "user.dir") "/test-structure-def.json")) (generate-string (sdef/generate-structure project) {:pretty true}))

    )
  )

