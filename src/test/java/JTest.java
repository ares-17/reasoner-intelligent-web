import org.example.MyReasoner;
import org.junit.Before;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class JTest {

    OWLReasoner hermitReasoner;
    private MyReasoner reasoner;
    private OWLDataFactory df;
    private IRI IOR;

    @Before
    public void setUp() throws Exception {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology o = man.loadOntologyFromOntologyDocument(new File("data/ONTOLOGY_TEST.owx"));
        this.df = man.getOWLDataFactory();
        this.IOR = o.getOntologyID().getOntologyIRI().get();
        this.reasoner = new MyReasoner(o);
        OWLReasonerFactory rf = new ReasonerFactory();
        this.hermitReasoner = rf.createReasoner(o);
        hermitReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
    }

    @org.junit.Test
    public void test1(){
        OWLClass class1 = df.getOWLClass(IOR + "#M");
        OWLClass class2 = df.getOWLClass(IOR + "#N");
        OWLClass inclusionClass = df.getOWLClass(IOR + "#B");
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(class1,class2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(intersectionOf,inclusionClass);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test2(){
        OWLClass class1 = df.getOWLClass(IOR + "#M");
        OWLClass class2 = df.getOWLClass(IOR + "#N");
        OWLClass inclusionClass = df.getOWLThing();
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(class1,class2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(intersectionOf,inclusionClass);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test3(){
        OWLClass class1 = df.getOWLClass(IOR + "#M");
        OWLClass class2 = df.getOWLClass(IOR + "#N");
        OWLClass inclusionClass = df.getOWLThing();
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(class1,class2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(inclusionClass,intersectionOf);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test4(){
        Set<OWLClass> set1 = new HashSet<>();
        set1.add(df.getOWLClass(IOR + "#M"));
        set1.add(df.getOWLClass(IOR + "#N"));
        set1.add(df.getOWLClass(IOR + "#I"));
        set1.add(df.getOWLClass(IOR + "#P"));
        OWLClass inclusionClass = df.getOWLClass(IOR + "#B");
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(set1);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(intersectionOf,inclusionClass);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));    }

    @org.junit.Test
    public void test5(){
        Set<OWLClass> set1 = new HashSet<>();
        Set<OWLClass> set2 = new HashSet<>();
        set1.add(df.getOWLClass(IOR + "#M"));
        set1.add(df.getOWLClass(IOR + "#N"));
        set1.add(df.getOWLClass(IOR + "#I"));
        set2.add(df.getOWLClass(IOR + "#P"));
        set2.add(df.getOWLClass(IOR + "#B"));
        OWLObjectIntersectionOf intersectionOfSub = df.getOWLObjectIntersectionOf(set1);
        OWLObjectIntersectionOf intersectionOfSuper = df.getOWLObjectIntersectionOf(set2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(intersectionOfSub,intersectionOfSuper);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));    }

    @org.junit.Test
    public void test6(){
        OWLIndividual individual = df.getOWLNamedIndividual(IOR + "#x");
        OWLObjectOneOf objectOneOf = df.getOWLObjectOneOf(individual);
        OWLClass classI = df.getOWLClass(IOR + "#I");
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectOneOf,classI);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));    }

    @org.junit.Test
    public void test7(){
        OWLIndividual individual = df.getOWLNamedIndividual(IOR + "#x");
        OWLObjectOneOf objectOneOf = df.getOWLObjectOneOf(individual);
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectOneOf,classA);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));    }

    @org.junit.Test
    public void test8(){
        OWLIndividual individual = df.getOWLNamedIndividual(IOR + "#x");
        OWLObjectOneOf objectOneOf = df.getOWLObjectOneOf(individual);
        OWLObjectProperty r = df.getOWLObjectProperty(IOR + "#r3");
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLClass classB = df.getOWLClass(IOR + "#B");
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(classA,classB);
        OWLObjectSomeValuesFrom objectSomeValuesFrom = df.getOWLObjectSomeValuesFrom(r,intersectionOf);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectOneOf,objectSomeValuesFrom);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));    }

    @org.junit.Test
    public void test9(){
        OWLIndividual individual = df.getOWLNamedIndividual(IOR + "#x");
        OWLObjectOneOf objectOneOf = df.getOWLObjectOneOf(individual);
        OWLObjectProperty r = df.getOWLObjectProperty(IOR + "#r2");
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLClass classB = df.getOWLClass(IOR + "#B");
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(classA,classB);
        OWLObjectSomeValuesFrom objectSomeValuesFrom = df.getOWLObjectSomeValuesFrom(r,intersectionOf);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectOneOf,objectSomeValuesFrom);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));    }

    @org.junit.Test
    public void test10(){
        OWLIndividual individual = df.getOWLNamedIndividual(IOR + "#x");
        OWLObjectOneOf objectOneOf = df.getOWLObjectOneOf(individual);
        OWLObjectProperty r = df.getOWLObjectProperty(IOR + "#r2");
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLClass classB = df.getOWLClass(IOR + "#B");
        OWLClass classI = df.getOWLClass(IOR + "#I");
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(classA,classB,classI);
        OWLObjectSomeValuesFrom objectSomeValuesFrom = df.getOWLObjectSomeValuesFrom(r,intersectionOf);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectOneOf,objectSomeValuesFrom);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));    }

    @org.junit.Test
    public void test11(){
        OWLIndividual individualX = df.getOWLNamedIndividual(IOR + "#x");
        OWLObjectOneOf objectOneOfX = df.getOWLObjectOneOf(individualX);
        OWLIndividual individualY = df.getOWLNamedIndividual(IOR + "#y");
        OWLObjectOneOf objectOneOfY = df.getOWLObjectOneOf(individualY);
        OWLObjectProperty r = df.getOWLObjectProperty(IOR + "#r4");
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLClass classB = df.getOWLClass(IOR + "#B");
        OWLClass classG = df.getOWLClass(IOR + "#G");
        OWLObjectIntersectionOf intersectionOfSub = df.getOWLObjectIntersectionOf(objectOneOfX,objectOneOfY);
        OWLObjectIntersectionOf intersectionOfSuper = df.getOWLObjectIntersectionOf(classA,classB,classG);
        OWLObjectSomeValuesFrom objectSomeValuesFrom = df.getOWLObjectSomeValuesFrom(r,intersectionOfSuper);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(intersectionOfSub,objectSomeValuesFrom);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));    }

    @org.junit.Test
    public void test12(){
        OWLIndividual individualX = df.getOWLNamedIndividual(IOR + "#x");
        OWLObjectOneOf objectOneOfX = df.getOWLObjectOneOf(individualX);
        OWLIndividual individualY = df.getOWLNamedIndividual(IOR + "#y");
        OWLObjectOneOf objectOneOfY = df.getOWLObjectOneOf(individualY);
        OWLObjectProperty r = df.getOWLObjectProperty(IOR + "#r4");
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLClass classB = df.getOWLClass(IOR + "#B");
        OWLObjectIntersectionOf intersectionOfSub = df.getOWLObjectIntersectionOf(objectOneOfX,objectOneOfY);
        OWLObjectIntersectionOf intersectionOfSuper = df.getOWLObjectIntersectionOf(classA,classB);
        OWLObjectSomeValuesFrom objectSomeValuesFrom = df.getOWLObjectSomeValuesFrom(r,intersectionOfSuper);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(intersectionOfSub,objectSomeValuesFrom);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test13(){
        OWLIndividual individualY = df.getOWLNamedIndividual(IOR + "#y");
        OWLObjectOneOf objectOneOfY = df.getOWLObjectOneOf(individualY);
        OWLObjectProperty r = df.getOWLObjectProperty(IOR + "#r4");
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLClass classB = df.getOWLClass(IOR + "#B");
        OWLObjectIntersectionOf intersectionOfSuper = df.getOWLObjectIntersectionOf(classA,classB);
        OWLObjectSomeValuesFrom objectSomeValuesFrom = df.getOWLObjectSomeValuesFrom(r,intersectionOfSuper);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectOneOfY,objectSomeValuesFrom);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test14() {
        OWLClass classF = df.getOWLClass(IOR + "#F");
        OWLClass classI = df.getOWLClass(IOR + "#I");
        OWLClass classL = df.getOWLClass(IOR + "#L");
        OWLObjectProperty r3 = df.getOWLObjectProperty(IOR + "#r3");
        OWLObjectProperty r4 = df.getOWLObjectProperty(IOR + "#r4");
        OWLObjectSomeValuesFrom objectSomeValuesFrom4L = df.getOWLObjectSomeValuesFrom(r4, classL);
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(classI, objectSomeValuesFrom4L);
        OWLObjectSomeValuesFrom objectSomeValuesFromR3 = df.getOWLObjectSomeValuesFrom(r3, intersectionOf);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(classF, objectSomeValuesFromR3);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(), complementOf);
        assertEquals(reasoner.doQuery(query), !hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test15(){
        OWLClass classC = df.getOWLClass(IOR + "#C");
        OWLClass classD = df.getOWLClass(IOR + "#D");
        OWLClass classS = df.getOWLClass(IOR + "#S");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r2");
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(classC,classD);
        OWLObjectSomeValuesFrom objectSomeValuesFromR3= df.getOWLObjectSomeValuesFrom(r2,intersectionOf);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromR3,classS);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test16(){
        OWLClass classC = df.getOWLClass(IOR + "#C");
        OWLClass classD = df.getOWLClass(IOR + "#D");
        OWLClass classE = df.getOWLClass(IOR + "#E");
        OWLClass classF = df.getOWLClass(IOR + "#F");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r2");
        OWLObjectProperty r4 = df.getOWLObjectProperty(IOR + "#r4");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classC,classD);
        OWLObjectIntersectionOf intersectionOf2 = df.getOWLObjectIntersectionOf(classF,classE);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromR4= df.getOWLObjectSomeValuesFrom(r4,intersectionOf2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromR2,objectSomeValuesFromR4);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test17(){
        OWLClass classC = df.getOWLClass(IOR + "#C");
        OWLClass classD = df.getOWLClass(IOR + "#D");
        OWLClass classE = df.getOWLClass(IOR + "#E");
        OWLClass classF = df.getOWLClass(IOR + "#F");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r3");
        OWLObjectProperty r4 = df.getOWLObjectProperty(IOR + "#r4");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classC,classD);
        OWLObjectIntersectionOf intersectionOf2 = df.getOWLObjectIntersectionOf(classF,classE);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromR4= df.getOWLObjectSomeValuesFrom(r4,intersectionOf2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromR2,objectSomeValuesFromR4);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test18(){
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLClass classD = df.getOWLClass(IOR + "#D");
        OWLClass classE = df.getOWLClass(IOR + "#E");
        OWLClass classF = df.getOWLClass(IOR + "#F");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r2");
        OWLObjectProperty r4 = df.getOWLObjectProperty(IOR + "#r4");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classA,classD);
        OWLObjectIntersectionOf intersectionOf2 = df.getOWLObjectIntersectionOf(classF,classE);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromR4= df.getOWLObjectSomeValuesFrom(r4,intersectionOf2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromR2,objectSomeValuesFromR4);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test19(){
        OWLClass classC = df.getOWLClass(IOR + "#C");
        OWLClass classD = df.getOWLClass(IOR + "#D");
        OWLClass classE = df.getOWLClass(IOR + "#E");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r2");
        OWLObjectProperty r4 = df.getOWLObjectProperty(IOR + "#r4");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classC,classD);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromR4= df.getOWLObjectSomeValuesFrom(r4,classE);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromR2,objectSomeValuesFromR4);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test20(){
        OWLClass classA = df.getOWLClass(IOR + "#B");
        OWLClass classD = df.getOWLClass(IOR + "#D");
        OWLClass classE = df.getOWLClass(IOR + "#E");
        OWLObjectProperty r5 = df.getOWLObjectProperty(IOR + "#r5");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r2");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classE,classD);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromForQuery= df.getOWLObjectSomeValuesFrom(r5,objectSomeValuesFromR2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromForQuery,classA);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test21(){
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLClass classB = df.getOWLClass(IOR + "#B");
        OWLClass classE = df.getOWLClass(IOR + "#E");
        OWLObjectProperty r1 = df.getOWLObjectProperty(IOR + "#r1");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r2");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classA,classB);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromForQuery= df.getOWLObjectSomeValuesFrom(r1,objectSomeValuesFromR2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromForQuery,classE);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test22(){
        OWLClass classP = df.getOWLClass(IOR + "#P");
        OWLClass classO = df.getOWLClass(IOR + "#O");
        OWLClass classD = df.getOWLClass(IOR + "#D");
        OWLClass classE = df.getOWLClass(IOR + "#E");
        OWLObjectProperty r5 = df.getOWLObjectProperty(IOR + "#r5");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r2");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classE,classD);
        OWLObjectIntersectionOf intersectionOf2 = df.getOWLObjectIntersectionOf(classO,classP);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromForQuery= df.getOWLObjectSomeValuesFrom(r5,objectSomeValuesFromR2);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromForQuery,intersectionOf2);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test23(){
        OWLClass classU = df.getOWLClass(IOR + "#U");
        OWLClass classV = df.getOWLClass(IOR + "#V");
        OWLClass classT = df.getOWLClass(IOR + "#T");
        OWLObjectProperty r1 = df.getOWLObjectProperty(IOR + "#r1");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r2");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classU,classV);
        OWLObjectSomeValuesFrom objectSomeValuesFromR1= df.getOWLObjectSomeValuesFrom(r1,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,classT);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromR1,objectSomeValuesFromR2);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test24(){
        OWLClass classU = df.getOWLClass(IOR + "#U");
        OWLClass classV = df.getOWLClass(IOR + "#V");
        OWLClass classT = df.getOWLClass(IOR + "#T");
        OWLObjectProperty r1 = df.getOWLObjectProperty(IOR + "#r1");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r3");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classU,classV);
        OWLObjectSomeValuesFrom objectSomeValuesFromR1= df.getOWLObjectSomeValuesFrom(r1,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,classT);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromR1,objectSomeValuesFromR2);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test
    public void test25(){
        OWLClass classU = df.getOWLClass(IOR + "#U");
        OWLClass classV = df.getOWLClass(IOR + "#V");
        OWLClass classT = df.getOWLClass(IOR + "#T");
        OWLObjectProperty r1 = df.getOWLObjectProperty(IOR + "#r1");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#r4");
        OWLObjectIntersectionOf intersectionOf1 = df.getOWLObjectIntersectionOf(classU,classV);
        OWLObjectSomeValuesFrom objectSomeValuesFromR1= df.getOWLObjectSomeValuesFrom(r1,intersectionOf1);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2= df.getOWLObjectSomeValuesFrom(r2,classT);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(objectSomeValuesFromR1,objectSomeValuesFromR2);
        OWLObjectComplementOf complementOf = df.getOWLObjectComplementOf(query.getSuperClass());
        OWLObjectIntersectionOf negIntersection = df.getOWLObjectIntersectionOf(query.getSubClass(),complementOf);
        assertEquals(reasoner.doQuery(query),!hermitReasoner.isSatisfiable(negIntersection));
    }

    @org.junit.Test (expected = IllegalArgumentException.class)
    public void testException1(){
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(classA,df.getOWLNothing());
        reasoner.doQuery(query);
    }

}
