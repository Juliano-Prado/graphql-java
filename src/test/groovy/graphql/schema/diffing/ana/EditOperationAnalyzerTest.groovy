package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
import spock.lang.Specification

import static graphql.schema.diffing.ana.SchemaDifference.*

class EditOperationAnalyzerTest extends Specification {

    def "object renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        schema {
         query: MyQuery 
        }
        type MyQuery {
            foo: String
        }
         
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] === changes.objectDifferences["MyQuery"]
        changes.objectDifferences["Query"] instanceof ObjectModification
        (changes.objectDifferences["Query"] as ObjectModification).oldName == "Query"
        (changes.objectDifferences["Query"] as ObjectModification).newName == "MyQuery"
    }

    def "interface renamed"() {
        given:
        def oldSdl = '''
        type Query implements I {
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query implements IRenamed {
            foo: String
        }
        interface IRenamed {
            foo: String
        }
         
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] === changes.interfaceDifferences["IRenamed"]
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        (changes.interfaceDifferences["I"] as InterfaceModification).oldName == "I"
        (changes.interfaceDifferences["I"] as InterfaceModification).newName == "IRenamed"
    }

    def "interface removed from object"() {
        given:
        def oldSdl = '''
        type Query implements I {
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query{
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def implementationDeletions = (changes.objectDifferences["Query"] as ObjectModification).getDetails(ObjectInterfaceImplementationDeletion)
        implementationDeletions[0].name == "I"
    }

    def "interface removed from interface"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        interface FooI {
            foo: String
        }
        interface Foo implements FooI {
            foo: String
        }
        type FooImpl implements Foo & FooI {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        interface Foo {
            foo: String
        }
        type FooImpl implements Foo {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["Foo"] instanceof InterfaceModification
        def implementationDeletions = (changes.interfaceDifferences["Foo"] as InterfaceModification).getDetails(InterfaceInterfaceImplementationDeletion)
        implementationDeletions[0].name == "FooI"
    }

    def "object and interface field renamed"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello: String
        }
        interface I {
            hello: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            hello2: String
        }
        interface I {
            hello2: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldRenames = objectModification.getDetails(ObjectFieldRename.class)
        oFieldRenames[0].oldName == "hello"
        oFieldRenames[0].newName == "hello2"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldRenames = interfaceModification.getDetails(InterfaceFieldRename.class)
        iFieldRenames[0].oldName == "hello"
        iFieldRenames[0].newName == "hello2"

    }

    def "object and interface field deleted"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello: String
            toDelete: String
        }
        interface I {
            hello: String
            toDelete: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            hello: String
        }
        interface I {
            hello: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldDeletions = objectModification.getDetails(ObjectFieldDeletion.class)
        oFieldDeletions[0].name == "toDelete"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldDeletions = interfaceModification.getDetails(InterfaceFieldDeletion.class)
        iFieldDeletions[0].name == "toDelete"

    }

    def "union added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B 
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionAddition
        (changes.unionDifferences["U"] as UnionAddition).name == "U"
    }

    def "union deleted"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            hello: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionDeletion
        (changes.unionDifferences["U"] as UnionDeletion).name == "U"
    }

    def "union renamed"() {
        given:
        def oldSdl = '''
        type Query {
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            u: X 
        }
        union X = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["X"] === changes.unionDifferences["U"]
        changes.unionDifferences["U"] instanceof UnionModification
        (changes.unionDifferences["U"] as UnionModification).oldName == "U"
        (changes.unionDifferences["U"] as UnionModification).newName == "X"
    }

    def "union renamed and member removed"() {
        given:
        def oldSdl = '''
        type Query {
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            u: X 
        }
        union X = A 
        type A {
            foo: String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def unionDiff = changes.unionDifferences["U"] as UnionModification
        unionDiff.oldName == "U"
        unionDiff.newName == "X"
        unionDiff.getDetails(UnionMemberDeletion)[0].name == "B"
    }

    def "union renamed and member added"() {
        given:
        def oldSdl = '''
        type Query {
            u: U 
        }
        union U = A 
        type A {
            foo: String
        } 

        '''
        def newSdl = '''
        type Query {
            u: X
        }
        union X = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def unionDiff = changes.unionDifferences["U"] as UnionModification
        unionDiff.oldName == "U"
        unionDiff.newName == "X"
        unionDiff.getDetails(UnionMemberAddition)[0].name == "B"
    }

    def "union member added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B | C
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        type C {
            foo: String
        } 

        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def unionModification = changes.unionDifferences["U"] as UnionModification
        unionModification.getDetails(UnionMemberAddition)[0].name == "C"
    }

    def "union member deleted"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A 
        type A {
            foo: String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def unionModification = changes.unionDifferences["U"] as UnionModification
        unionModification.getDetails(UnionMemberDeletion)[0].name == "B"
    }

    def "field type modified"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String!
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def typeModification = objectModification.getDetails(ObjectFieldTypeModification.class)
        typeModification[0].oldType == "String"
        typeModification[0].newType == "String!"
    }

    def "object and interface field argument renamed"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello(arg: String): String
        }
        interface I {
            hello(arg: String): String
        } 
        '''
        def newSdl = '''
        type Query implements I{
            hello(argRename: String): String
        }
        interface I {
            hello(argRename: String): String
        } 
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def objectArgumentRenamed = objectModification.getDetails(ObjectFieldArgumentRename.class);
        objectArgumentRenamed[0].oldName == "arg"
        objectArgumentRenamed[0].newName == "argRename"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["I"] as InterfaceModification
        def interfaceArgumentRenamed = interfaceModification.getDetails(InterfaceFieldArgumentRename.class);
        interfaceArgumentRenamed[0].oldName == "arg"
        interfaceArgumentRenamed[0].newName == "argRename"

    }


    def "object field argument removed"() {
        given:
        def oldSdl = '''
        type Query {
            hello(arg: String): String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def argumentRemoved = objectModification.getDetails(ObjectFieldArgumentDeletion.class);
        argumentRemoved[0].fieldName == "hello"
        argumentRemoved[0].name == "arg"
    }

    def "argument default value modified for Object and Interface"() {
        given:
        def oldSdl = '''
        type Query implements Foo {
            foo(arg: String = "bar"): String
        }
        interface Foo {
            foo(arg: String = "bar"): String
        }
        
        '''
        def newSdl = '''
        type Query implements Foo {
            foo(arg: String = "barChanged"): String
        }
        interface Foo {
            foo(arg: String = "barChanged"): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)

        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def objDefaultValueModified = objectModification.getDetails(ObjectFieldArgumentDefaultValueModification.class);
        objDefaultValueModified[0].fieldName == "foo"
        objDefaultValueModified[0].argumentName == "arg"
        objDefaultValueModified[0].oldValue == '"bar"'
        objDefaultValueModified[0].newValue == '"barChanged"'
        and:
        changes.interfaceDifferences["Foo"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceDifferences["Foo"] as InterfaceModification
        def intDefaultValueModified = interfaceModification.getDetails(InterfaceFieldArgumentDefaultValueModification.class);
        intDefaultValueModified[0].fieldName == "foo"
        intDefaultValueModified[0].argumentName == "arg"
        intDefaultValueModified[0].oldValue == '"bar"'
        intDefaultValueModified[0].newValue == '"barChanged"'
    }

    def "object and interface field added"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello: String
        }
        interface I {
            hello: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            hello: String
            newOne: String
        }
        interface I {
            hello: String
            newOne: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldAdded = objectModification.getDetails(ObjectFieldAddition)
        oFieldAdded[0].name == "newOne"
        and:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def iInterfaces = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldAdded = iInterfaces.getDetails(InterfaceFieldAddition)
        iFieldAdded[0].name == "newOne"

    }

    def "object added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
            foo: Foo
        }
        type Foo {
            id: ID
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Foo"] instanceof ObjectAddition
    }

    def "object removed and field type changed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Foo"] instanceof ObjectDeletion
        (changes.objectDifferences["Foo"] as ObjectDeletion).name == "Foo"
        changes.objectDifferences["Query"] instanceof ObjectModification
        def queryObjectModification = changes.objectDifferences["Query"] as ObjectModification
        queryObjectModification.details.size() == 1
        queryObjectModification.details[0] instanceof ObjectFieldTypeModification
        (queryObjectModification.details[0] as ObjectFieldTypeModification).oldType == "Foo"
        (queryObjectModification.details[0] as ObjectFieldTypeModification).newType == "String"

    }

    def "Interface and Object field type changed completely"() {
        given:
        def oldSdl = '''
        type Query implements I{
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            foo: ID
        }
        interface I {
            foo: ID
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def iModification = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldTypeModifications = iModification.getDetails(InterfaceFieldTypeModification)
        iFieldTypeModifications[0].fieldName == "foo"
        iFieldTypeModifications[0].oldType == "String"
        iFieldTypeModifications[0].newType == "ID"
        and:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def oModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldTypeModifications = oModification.getDetails(ObjectFieldTypeModification)
        oFieldTypeModifications[0].fieldName == "foo"
        oFieldTypeModifications[0].oldType == "String"
        oFieldTypeModifications[0].newType == "ID"
    }

    def "Interface and Object field type changed wrapping type"() {
        given:
        def oldSdl = '''
        type Query implements I{
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            foo: [String!]
        }
        interface I {
            foo: [String!]
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def iModification = changes.interfaceDifferences["I"] as InterfaceModification
        def iFieldTypeModifications = iModification.getDetails(InterfaceFieldTypeModification)
        iFieldTypeModifications[0].fieldName == "foo"
        iFieldTypeModifications[0].oldType == "String"
        iFieldTypeModifications[0].newType == "[String!]"
        and:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def oModification = changes.objectDifferences["Query"] as ObjectModification
        def oFieldTypeModifications = oModification.getDetails(ObjectFieldTypeModification)
        oFieldTypeModifications[0].fieldName == "foo"
        oFieldTypeModifications[0].oldType == "String"
        oFieldTypeModifications[0].newType == "[String!]"


    }

    def "new Interface introduced"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        type Foo {
          id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences.size() == 1
        changes.interfaceDifferences["Node"] instanceof InterfaceAddition
        changes.objectDifferences.size() == 1
        changes.objectDifferences["Foo"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Foo"] as ObjectModification
        def addedInterfaceDetails = objectModification.getDetails(ObjectInterfaceImplementationAddition.class)
        addedInterfaceDetails.size() == 1
        addedInterfaceDetails[0].name == "Node"
    }

    def "Object and Interface added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences.size() == 1
        changes.interfaceDifferences["Node"] instanceof InterfaceAddition
        changes.objectDifferences.size() == 2
        changes.objectDifferences["Foo"] instanceof ObjectAddition
        changes.objectDifferences["Query"] instanceof ObjectModification
        (changes.objectDifferences["Query"] as ObjectModification).getDetails()[0] instanceof ObjectFieldTypeModification
    }

    def "interfaced renamed"() {
        given:
        def oldSdl = '''
        type Query {
          foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        interface Node2 {
            id: ID!
        }
        type Foo implements Node2{
            id: ID!
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences.size() == 2
        changes.interfaceDifferences["Node"] === changes.interfaceDifferences["Node2"]
        changes.interfaceDifferences["Node2"] instanceof InterfaceModification
    }

    def "interfaced renamed and another interface added to it"() {
        given:
        def oldSdl = '''
        type Query {
          foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        interface NewI {
            hello: String
        }
        interface Node2 {
            id: ID!
        }
        type Foo implements Node2 & NewI{
            id: ID!
            hello: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences.size() == 3
        changes.interfaceDifferences["Node"] == changes.interfaceDifferences["Node2"]
        changes.interfaceDifferences["Node2"] instanceof InterfaceModification
        changes.interfaceDifferences["NewI"] instanceof InterfaceAddition
        changes.objectDifferences.size() == 1
        changes.objectDifferences["Foo"] instanceof ObjectModification
        def objectModification = changes.objectDifferences["Foo"] as ObjectModification
        def addedInterfaceDetails = objectModification.getDetails(ObjectInterfaceImplementationAddition)
        addedInterfaceDetails.size() == 1
        addedInterfaceDetails[0].name == "NewI"

    }

    def "enum renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        enum E {
            A, B
        }
        '''
        def newSdl = '''
        type Query {
            foo: ERenamed
        }
        enum ERenamed {
            A, B
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] === changes.enumDifferences["ERenamed"]
        def modification = changes.enumDifferences["E"] as EnumModification
        modification.oldName == "E"
        modification.newName == "ERenamed"

    }

    def "enum added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: E
        }
        enum E {
            A, B
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumAddition
        (changes.enumDifferences["E"] as EnumAddition).getName() == "E"
    }

    def "enum deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: E
        }
        enum E {
            A, B
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumDeletion
        (changes.enumDifferences["E"] as EnumDeletion).getName() == "E"
    }


    def "enum value added"() {
        given:
        def oldSdl = '''
        type Query {
            e: E
        }
        enum E {
            A
        }
        '''
        def newSdl = '''
        type Query {
            e: E
        }
        enum E {
            A, B
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def enumModification = changes.enumDifferences["E"] as EnumModification
        enumModification.getDetails(EnumValueAddition)[0].name == "B"
    }

    def "enum value deleted"() {
        given:
        def oldSdl = '''
        type Query {
            e: E
        }
        enum E {
            A,B
        }
        '''
        def newSdl = '''
        type Query {
            e: E
        }
        enum E {
            A
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def enumModification = changes.enumDifferences["E"] as EnumModification
        enumModification.getDetails(EnumValueDeletion)[0].name == "B"
    }

    def "scalar added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: E
        }
        scalar E
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["E"] instanceof ScalarAddition
        (changes.scalarDifferences["E"] as ScalarAddition).getName() == "E"
    }

    def "scalar deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: E
        }
        scalar E
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["E"] instanceof ScalarDeletion
        (changes.scalarDifferences["E"] as ScalarDeletion).getName() == "E"
    }

    def "scalar renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        scalar Foo
        '''
        def newSdl = '''
        type Query {
            foo: Bar
        }
        scalar Bar
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["Foo"] === changes.scalarDifferences["Bar"]
        def modification = changes.scalarDifferences["Foo"] as ScalarModification
        modification.oldName == "Foo"
        modification.newName == "Bar"
    }

    def "input object added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectAddition
        (changes.inputObjectDifferences["I"] as InputObjectAddition).getName() == "I"
    }

    def "input object deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectDeletion
        (changes.inputObjectDifferences["I"] as InputObjectDeletion).getName() == "I"
    }

    def "input object renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String 
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: IRenamed): String 
        }
        input IRenamed {
            bar: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] === changes.inputObjectDifferences["IRenamed"]
        def modification = changes.inputObjectDifferences["I"] as InputObjectModification
        modification.oldName == "I"
        modification.newName == "IRenamed"
    }


    def "directive added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveAddition
        (changes.directiveDifferences["d"] as DirectiveAddition).getName() == "d"
    }

    def "directive deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveDeletion
        (changes.directiveDifferences["d"] as DirectiveDeletion).getName() == "d"
    }

    def "directive renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @dRenamed on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] === changes.directiveDifferences["dRenamed"]
        def modification = changes.directiveDifferences["d"] as DirectiveModification
        modification.oldName == "d"
        modification.newName == "dRenamed"
    }

    def "directive argument renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo: String) on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d(bar:String) on FIELD
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d"] instanceof DirectiveModification
        def renames = (changes.directiveDifferences["d"] as DirectiveModification).getDetails(DirectiveArgumentRename)
        renames[0].oldName == "foo"
        renames[0].newName == "bar"


    }


    EditOperationAnalysisResult calcDiff(
            String oldSdl,
            String newSdl
    ) {
        def oldSchema = TestUtil.schema(oldSdl)
        def newSchema = TestUtil.schema(newSdl)
        def changes = new SchemaDiffing().diffAndAnalyze(oldSchema, newSchema)
        return changes
    }
}
