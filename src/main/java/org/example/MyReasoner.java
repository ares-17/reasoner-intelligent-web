package org.example;

import javafx.util.Pair;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.*;

public class MyReasoner {

    private final OWLOntology ontology;
    private final OWLDataFactory df;
    private int universalTempCount = 0;
    private Set<OWLSubClassOfAxiom> normalizedAxiomsSet = null;
    private Map<OWLClassExpression, Set<OWLClassExpression>> S = null;
    private Map<OWLObjectPropertyExpression, Set<Pair<OWLClassExpression, OWLClassExpression>>> R = null;

    public MyReasoner(OWLOntology o) {
        this.ontology = o;
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        this.df = man.getOWLDataFactory();
        Set<OWLAxiom> subClassOfAxioms = this.ontology.getTBoxAxioms(Imports.EXCLUDED);
        this.normalizedAxiomsSet = normalization(subClassOfAxioms);
        this.S = new HashMap<>();
        this.R = new HashMap<>();
    }

    public boolean doQuery(OWLSubClassOfAxiom query) {
        Set<OWLAxiom> fictitiousSet;
        Set<OWLSubClassOfAxiom> mergedSubAxiomsSet = new HashSet<>();

          OWLClassExpression subClass = query.getSubClass();
          OWLClassExpression superClass = query.getSuperClass();
        fictitiousSet = createFictitious(subClass, superClass);
        for(OWLAxiom ax : fictitiousSet){
            OWLSubClassOfAxiom cast = (OWLSubClassOfAxiom) ax;
            OWLClassExpression subClass2 = cast.getSubClass();
            OWLClassExpression superClass2 = cast.getSuperClass();
            subAndSuperCheckBottom(subClass2, superClass2);
        }
        mergedSubAxiomsSet.addAll(this.normalizedAxiomsSet);
        mergedSubAxiomsSet.addAll(normalization(fictitiousSet));
        initializeMapping(mergedSubAxiomsSet);
        applyingCompletionRules(mergedSubAxiomsSet);
        return this.S.get(this.df.getOWLClass("#FIT0")).contains(this.df.getOWLClass("#FIT1"));
    }

    private Set<OWLAxiom> createFictitious(OWLClassExpression subClass, OWLClassExpression superClass) {
        Set<OWLAxiom> returnSet = new HashSet<>();
        OWLClass fit0 = this.df.getOWLClass(IRI.create("#FIT0"));
        OWLClass fit1 = this.df.getOWLClass(IRI.create("#FIT1"));
        OWLSubClassOfAxiom sub1 = this.df.getOWLSubClassOfAxiom(fit0, subClass);
        OWLSubClassOfAxiom sub2 = this.df.getOWLSubClassOfAxiom(superClass, fit1);
        returnSet.add(sub1);
        returnSet.add(sub2);
        return returnSet;
    }

    private void initializeMapping(Set<OWLSubClassOfAxiom> normalizedAxSet) {
        for (OWLSubClassOfAxiom ax : normalizedAxSet) {
            OWLClassExpression subClass = ax.getSubClass();
            OWLClassExpression superClass = ax.getSuperClass();
            initializeSingleMapping(subClass);
            initializeSingleMapping(superClass);
        }
    }

    private void initializeSingleMapping(OWLClassExpression expression) {
        Set<OWLClassExpression> setS = new HashSet<>();
        if (expression.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS) ||
                expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_ONE_OF)) {
            setS.add(expression);
            setS.add(this.df.getOWLThing());
            S.put(expression, setS);
        } else if (expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) {
            OWLObjectIntersectionOf intersectionOf = (OWLObjectIntersectionOf) expression;
            ArrayList<OWLClassExpression> twoClasses = new ArrayList<>(intersectionOf.getOperandsAsList());
            setS.add(twoClasses.get(0));
            setS.add(this.df.getOWLThing());
            S.put(twoClasses.get(0), setS);
            setS = new HashSet<>();
            setS.add(twoClasses.get(1));
            setS.add(this.df.getOWLThing());
            S.put(twoClasses.get(1), setS);
        } else if (expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
            Set<Pair<OWLClassExpression, OWLClassExpression>> setR = new HashSet<>();
            OWLObjectSomeValuesFrom cast = (OWLObjectSomeValuesFrom) expression;
            R.put(cast.getProperty(), setR);
            setS.add(cast.getFiller()); //Aggiungo al setS la classe (o singleton) dell'esistenziale
            setS.add(this.df.getOWLThing()); //Aggiungo il TOP
            S.put(cast.getFiller(), setS); //Inserisco nella mappa S la classe (o singleton) dell'esistenziale e il setS creato per essa
        }
    }

    private void applyingCompletionRules(Set<OWLSubClassOfAxiom> mergedSubClassAxioms) {
        boolean repeatLoop = true;
        List<Boolean> checkCR = new LinkedList<>();
        while (repeatLoop) {
            repeatLoop = false;
            for (OWLClassExpression key : this.S.keySet()) {
                checkCR.add(CR1(key, mergedSubClassAxioms));
                checkCR.add(CR2(key, mergedSubClassAxioms));
                checkCR.add(CR3(key, mergedSubClassAxioms));

                for (Boolean b : checkCR) {
                    if (b) {
                        repeatLoop = true;
                        break;
                    }
                }
                checkCR.clear();
            }
            for(OWLObjectPropertyExpression key : this.R.keySet()){
                checkCR.add(CR4(key, mergedSubClassAxioms));
                checkCR.add(CR5(key));
                for (Boolean b : checkCR) {
                    if (b) {
                        repeatLoop = true;
                        break;
                    }
                }
                checkCR.clear();
            }
            DefaultDirectedGraph<OWLClassExpression, DefaultEdge> graphForCR6 = generateGraph();
            for(OWLClassExpression key1 : this.S.keySet()){
                for(OWLClassExpression key2 : this.S.keySet()){
                    checkCR.add(CR6(key1,key2,graphForCR6));
                    for (Boolean b : checkCR) {
                        if (b) {
                            repeatLoop = true;
                            break;
                        }
                    }
                    checkCR.clear();
                }
            }
        }
    }

    private boolean CR1(OWLClassExpression key, Set<OWLSubClassOfAxiom> mergedSubClassAxioms) {
        Set<OWLClassExpression> tempSet = new HashSet<>(this.S.get(key));
        boolean checkAdd, ret = false;
        for (OWLClassExpression setElementS : tempSet) { //Ciclo su ogni C' appartenente ad S(C)
            for (OWLSubClassOfAxiom sub : mergedSubClassAxioms) { //Ciclo su ogni sottoclasse della base di conoscenza
                OWLClassExpression leftOfSub = sub.getSubClass();
                OWLClassExpression superOfSub = sub.getSuperClass();
                if (!leftOfSub.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                    if (leftOfSub.equals(setElementS)) {
                        if (!superOfSub.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                            checkAdd = this.S.get(key).add(superOfSub);
                            if (checkAdd)
                                ret = true;
                        }
                    }
                }
            }
        }
        return ret;
    }

    private boolean CR2(OWLClassExpression key, Set<OWLSubClassOfAxiom> mergedSubClassAxioms) {
        boolean checkAdd, ret = false;
        List<OWLClassExpression> listClass = new ArrayList<>(this.S.get(key));
        for (int i = 0; i < listClass.size(); i++)
            for (int j = i + 1; j < listClass.size(); j++) {
                OWLObjectIntersectionOf intersectionOf = this.df.getOWLObjectIntersectionOf(listClass.get(i), listClass.get(j));
                for (OWLSubClassOfAxiom ax : mergedSubClassAxioms) {
                    OWLClassExpression subClass = ax.getSubClass();
                    OWLClassExpression superClass = ax.getSuperClass();
                    if (subClass.getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) {
                        if (subClass.equals(intersectionOf)) {
                            if (!superClass.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                                checkAdd = this.S.get(key).add(superClass);
                                if (checkAdd)
                                    ret = true;
                            }
                        }
                    }
                }
            }
        return ret;
    }

    private boolean CR3(OWLClassExpression key, Set<OWLSubClassOfAxiom> mergedSubClassAxioms) {
        boolean checkAdd, ret = false;
        Set<OWLClassExpression> tempSet = new HashSet<>(this.S.get(key));
        for (OWLClassExpression setElementS : tempSet) { //Ciclo su ogni C' appartenente ad S(C)
            for (OWLSubClassOfAxiom sub : mergedSubClassAxioms) {
                OWLClassExpression subClass = sub.getSubClass();
                OWLClassExpression superClass = sub.getSuperClass();
                if (!subClass.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                    if (subClass.equals(setElementS)) {
                        if (superClass.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                            OWLObjectSomeValuesFrom castedSuperClass = (OWLObjectSomeValuesFrom) superClass;
                            OWLObjectPropertyExpression relation = castedSuperClass.getProperty();
                            OWLClassExpression filler = castedSuperClass.getFiller();
                            Pair<OWLClassExpression,OWLClassExpression> addPair = new Pair<>(key,filler);
                            checkAdd = this.R.get(relation).add(addPair);
                            if (checkAdd)
                                ret = true;
                        }
                    }
                }
            }
        }
        return ret;
    }

    private boolean CR4(OWLObjectPropertyExpression key, Set<OWLSubClassOfAxiom> mergedSubClassAxioms){
        Set<Pair<OWLClassExpression,OWLClassExpression>> setOfPair = this.R.get(key);
        boolean checkAdd, ret = false;
        for(Pair<OWLClassExpression,OWLClassExpression> pair : setOfPair){ //Ciclo sul set di Pair
            OWLClassExpression leftOfPair = pair.getKey(); //Elemento sinistro del Pair (C)
            OWLClassExpression rightOfPair = pair.getValue(); //Elemento destro del Pair (D)
            for(OWLClassExpression expression : this.S.get(rightOfPair)){ //Ciclo sul Set di S(D) e ottengo expression = D'
                for(OWLSubClassOfAxiom subClassOfAxiom : mergedSubClassAxioms){ //Ciclo sugli assiomi di sussunzione normalizzati
                    OWLClassExpression leftOfSub = subClassOfAxiom.getSubClass(); //Prendo lato sinistro della sussnzione
                    OWLClassExpression superOfSub = subClassOfAxiom.getSuperClass(); //Prendo lato destro della sussunzione (E)
                    if(leftOfSub.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)){ //Verifico che lato sinistro sia esiste(r.K)
                        OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) leftOfSub;
                        OWLObjectPropertyExpression relation = objectSomeValuesFrom.getProperty();
                        OWLClassExpression filler = objectSomeValuesFrom.getFiller(); //Prendo la parte interna dell'esistenziale (K)
                        if(relation.equals(key)) {
                            if (filler.equals(expression)) { //Verifico che K==D'
                                checkAdd = this.S.get(leftOfPair).add(superOfSub); //Aggiungo a S(C) E
                                if (checkAdd)
                                    ret = true;
                            }
                        }
                    }
                }
            }
        }
        return ret;
    }

    private boolean CR5(OWLObjectPropertyExpression key){
        Set<Pair<OWLClassExpression,OWLClassExpression>> setOfPair = this.R.get(key);
        boolean checkAdd, ret = false;
        for(Pair<OWLClassExpression,OWLClassExpression> pair : setOfPair){ //Ciclo sul set di Pair
            OWLClassExpression leftOfPair = pair.getKey(); //Elemento sinistro del Pair (C)
            OWLClassExpression rightOfPair = pair.getValue(); //Elemento destro del Pair (D)
            for(OWLClassExpression expression : this.S.get(rightOfPair)){ //Ciclo sul Set di S(D) e ottengo expression = D'
                if(expression.isOWLNothing()){ //Verifico se l'espressione è il Bottom
                    checkAdd = this.S.get(leftOfPair).add(this.df.getOWLNothing()); //Aggiungo a S(C) il Bottom
                    if (checkAdd)
                        ret = true;
                }
            }
        }
        return ret;
    }

    private boolean CR6(OWLClassExpression key1, OWLClassExpression key2, DefaultDirectedGraph<OWLClassExpression, DefaultEdge> graph){
        if(!key1.equals(key2) && !key1.isOWLNothing()){
            Set<OWLClassExpression> intersectionSetKey1AndKey2 = new HashSet<>(this.S.get(key1));
            intersectionSetKey1AndKey2.retainAll(this.S.get(key2));
            for(OWLClassExpression expression : intersectionSetKey1AndKey2){
                if(expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_ONE_OF)){
                    DijkstraShortestPath<OWLClassExpression,DefaultEdge> dijkstraShortestPath
                            = new DijkstraShortestPath<>(graph);
                    GraphPath<OWLClassExpression,DefaultEdge> path = dijkstraShortestPath.getPath(key1,key2);
                    if(path != null){
                        return this.S.get(key1).addAll(this.S.get(key2));
                    }
                    return false;
                }
            }
        }
        return false;
    }

    private DefaultDirectedGraph<OWLClassExpression, DefaultEdge> generateGraph(){
        DefaultDirectedGraph<OWLClassExpression, DefaultEdge> g = new DefaultDirectedGraph<>(DefaultEdge.class);
        for(OWLClassExpression expression : this.S.keySet()){
            g.addVertex(expression);
        }
        for(OWLObjectPropertyExpression r : this.R.keySet()){
            for(Pair<OWLClassExpression,OWLClassExpression> pair : this.R.get(r)){
                OWLClassExpression left = pair.getKey();
                OWLClassExpression right = pair.getValue();
                g.addEdge(left,right);
            }
        }
        return g;
    }

    private void checkBottom(OWLClassExpression expression) {
        Set<OWLClassExpression> set = new HashSet<>();
        set = expression.getNestedClassExpressions();
        for (OWLClassExpression ex : set) {
            if (ex.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS)) {
                OWLClass cl = (OWLClass) ex;
                if (cl.isBottomEntity()) {
                    throw new IllegalArgumentException("Trovato bottom in posizione non consentita");
                }
            }
        }
    }

    private void subAndSuperCheckBottom(OWLClassExpression subClass, OWLClassExpression superClass) {
        checkBottom(subClass);
        if (!superClass.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS)) {
            checkBottom(superClass);
        }
    }

    private Set<OWLSubClassOfAxiom> normalization(Set<OWLAxiom> subClassOfAxioms) {

        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> leftPair;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> rightPair;
        Set<OWLSubClassOfAxiom> resultSet = new HashSet<>();

        for (OWLAxiom ax : subClassOfAxioms) {

            OWLSubClassOfAxiom cast = (OWLSubClassOfAxiom) ax;
            OWLClassExpression subClass = cast.getSubClass();
            OWLClassExpression superClass = cast.getSuperClass();
            subAndSuperCheckBottom(subClass, superClass);

            leftPair = subClassNormalization(subClass);
            rightPair = superClassNormalization(superClass);

            resultSet.addAll(leftPair.getKey());    //Aggiungo al resultSet il set delle normalizzazioni
            resultSet.addAll(rightPair.getKey());   //Aggiungo al resultSet il set delle normalizzazioni
            if ((leftPair.getValue().getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM) ||
                    leftPair.getValue().getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) &&
                    rightPair.getValue().getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                leftPair = reduceToClass(leftPair.getValue());
                resultSet.addAll(leftPair.getKey());
            }
            OWLSubClassOfAxiom normalizedSubClass = this.df.getOWLSubClassOfAxiom(leftPair.getValue(), rightPair.getValue());
            resultSet.add(normalizedSubClass);
        }
        return resultSet;
    }

    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> subClassNormalization(OWLClassExpression subClass) {
        Set<OWLSubClassOfAxiom> set = new HashSet<>();
        ClassExpressionType typeSubClass = subClass.getClassExpressionType();
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair = null;

        if (typeSubClass.equals(ClassExpressionType.OWL_CLASS) || typeSubClass.equals(ClassExpressionType.OBJECT_ONE_OF)) { //Verifica se è una classe semplice
            returnPair = new Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression>(set, subClass);
        } else if (typeSubClass.equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) { //Verifica se è intersezione
            OWLObjectIntersectionOf intersectionOf = (OWLObjectIntersectionOf) subClass;
            returnPair = normalizeIntersectionOf(intersectionOf); //Il pair è ritornato dalla funzione chiamata
        } else if (typeSubClass.equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
            OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) subClass;
            returnPair = normalizeObjectSomeValueFrom(objectSomeValuesFrom);
        }
        return returnPair;
    }

    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> superClassNormalization(OWLClassExpression superClass) {
        Set<OWLSubClassOfAxiom> set = new HashSet<>();
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair = null;

        if (superClass.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS) ||
                superClass.getClassExpressionType().equals(ClassExpressionType.OBJECT_ONE_OF)) { //Verifica se è una classe semplice
            returnPair = new Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression>(set, superClass);
        } else if (superClass.getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) { //Verifica se è intersezione
            OWLObjectIntersectionOf intersectionOf = (OWLObjectIntersectionOf) superClass;
            tempPair = normalizeIntersectionOf(intersectionOf);
            returnPair = reduceToClass(tempPair.getValue());
            returnPair.getKey().addAll(tempPair.getKey());
            return returnPair;
        } else if (superClass.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
            OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) superClass;
            returnPair = normalizeObjectSomeValueFrom(objectSomeValuesFrom);
        }
        return returnPair;
    }

    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> normalizeIntersectionOf(OWLObjectIntersectionOf intersectionOf) {
        ArrayList<OWLClassExpression> arrayListOfExpressions = new ArrayList<>(intersectionOf.getOperandsAsList());
        int size = arrayListOfExpressions.size();

        List<OWLClassExpression> setTempClasses = new ArrayList<>();
        Set<OWLSubClassOfAxiom> returnSet = new HashSet<>();
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair = null;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair = null;

        if (size == 2) {
            if (arrayListOfExpressions.get(0).getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) arrayListOfExpressions.get(0);
                tempPair = normalizeSomeValuesFromAsClass(objectSomeValuesFrom);
                returnSet.addAll(tempPair.getKey());
                arrayListOfExpressions.set(0, tempPair.getValue());
            }
            if (arrayListOfExpressions.get(1).getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) arrayListOfExpressions.get(1);
                tempPair = normalizeSomeValuesFromAsClass(objectSomeValuesFrom);
                returnSet.addAll(tempPair.getKey());
                arrayListOfExpressions.set(1, tempPair.getValue());
            }
            OWLObjectIntersectionOf newIntersectionOf = this.df.getOWLObjectIntersectionOf(arrayListOfExpressions.get(0), arrayListOfExpressions.get(1));
            return new Pair<>(returnSet, newIntersectionOf);
        }

        for (int i = 0; i < size; i++) {
            if (arrayListOfExpressions.get(i).getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) arrayListOfExpressions.get(i);
                tempPair = normalizeSomeValuesFromAsClass(objectSomeValuesFrom);
                returnSet.addAll(tempPair.getKey());
                arrayListOfExpressions.set(i, tempPair.getValue()); //La classe generata viene assegnata ad arrayListOfExpressions[i]
            }
            if (i % 2 != 0) {
                OWLClass tempClass = createTempClass();
                setTempClasses.add(tempClass); //Necessario per creare intersezione per chiamata ricorsiva
                returnSet.addAll(normalizeSingleIntersectionOf(arrayListOfExpressions.get(i - 1), arrayListOfExpressions.get(i), tempClass));
            }
        }

        if (size % 2 != 0) {
            setTempClasses.add(arrayListOfExpressions.get(size - 1));
        }

        OWLObjectIntersectionOf intersectionRecur = this.df.getOWLObjectIntersectionOf(setTempClasses); //Creo intersezione per ricorsione
        returnPair = normalizeIntersectionOf(intersectionRecur); //RICORSIONE
        returnPair.getKey().addAll(returnSet); //Aggiunta elementi al set (solo SubClasses)
        return returnPair;
    }

    //TORNA ESISTENZIALE DI UNA CLASSE (Exist(r.C))
    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> normalizeObjectSomeValueFrom(OWLObjectSomeValuesFrom someValuesFrom) {
        OWLObjectPropertyExpression relation = someValuesFrom.getProperty();
        OWLClassExpression filler = someValuesFrom.getFiller();

        Set<OWLSubClassOfAxiom> returnSet = new HashSet<>();
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair = null;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> reduceToClassPair = null;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair = null;

        if (filler.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS) ||
                filler.getClassExpressionType().equals(ClassExpressionType.OBJECT_ONE_OF)) {
            return new Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression>(returnSet, someValuesFrom);
        } else if (filler.getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) {
            tempPair = normalizeIntersectionOf((OWLObjectIntersectionOf) filler); //Torna pair di set e un and singolo
            reduceToClassPair = reduceToClass(tempPair.getValue()); //Prende l'and singolo e lo riduce ad una classe TEMP
            reduceToClassPair.getKey().addAll((tempPair.getKey()));
            OWLObjectSomeValuesFrom normalizedSomeValuesFrom = this.df.getOWLObjectSomeValuesFrom(relation, reduceToClassPair.getValue());
            returnPair = new Pair<>(reduceToClassPair.getKey(), normalizedSomeValuesFrom);
        }
        //REMINDER: TORNARE ESISTENZIALE DI CLASSE (REDUCETOCLASSPAIR POTREBBE ESSERE VUOTO DOPO IF)
        else if (filler.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
            tempPair = normalizeObjectSomeValueFrom((OWLObjectSomeValuesFrom) filler); //Torna un set e una classe temp o esistenziale
            OWLClassExpression expression = tempPair.getValue(); //Prendo l'espressione a destra della coppia (che è esistenziale di una classe)
            reduceToClassPair = new Pair<>(tempPair.getKey(), expression); //Inizializzo Pair con contenuto uguale a tempPair

            if (expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
                reduceToClassPair = reduceToClass(expression); //trasformo l'esistenziale nuovo in una variabile temp
                tempPair.getKey().addAll(reduceToClassPair.getKey()); //Aggiungo nel Set gli assiomi di reduceToClass (se non entrato in if non aggiunge nulla)
            }
            //Creo esistenziale con quello di ora con il temp creato prima
            OWLObjectSomeValuesFrom normalizedSomeValuesFrom = this.df.getOWLObjectSomeValuesFrom(relation, reduceToClassPair.getValue());
            returnPair = new Pair<>(tempPair.getKey(), normalizedSomeValuesFrom); //Creo il Pair di ritorno con insieme di assiomi + esistenziale normalizzato
        }
        return returnPair;
    }

    private OWLClass createTempClass() {
        OWLClass tempClass = this.df.getOWLClass(IRI.create("#TEMP" + universalTempCount));
        universalTempCount++;
        return tempClass;
    }

    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> normalizeSomeValuesFromAsClass(OWLObjectSomeValuesFrom objectSomeValuesFrom) {
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair = null;
        Set<OWLSubClassOfAxiom> returnSet = new HashSet<>();

        tempPair = normalizeObjectSomeValueFrom(objectSomeValuesFrom); //Norm. Exist. torna Pair di assiomi e esistenziale (Exist(r.C))
        returnSet.addAll(tempPair.getKey()); //Aggiungo gli assiomi generati durante la normalizzazione al set di assiomi globale
        tempPair = reduceToClass(tempPair.getValue()); //Riduco a classe l'esistenziale attuale (perché siamo in una serie di and)
        returnSet.addAll(tempPair.getKey()); //Aggiungo gli assiomi generati durante la riduzione a classe dell'esistenziale

        return new Pair<>(returnSet, tempPair.getValue());
    }

    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> reduceToClass(OWLClassExpression expression) {
        OWLClass tempClass = createTempClass();
        ArrayList<OWLClassExpression> arrayListOfExpressions;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair = null;
        if (expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) {
            OWLObjectIntersectionOf intersectionOf = (OWLObjectIntersectionOf) expression;
            arrayListOfExpressions = new ArrayList<>(intersectionOf.getOperandsAsList());
            returnPair = new Pair<>(normalizeSingleIntersectionOf(arrayListOfExpressions.get(0),
                    arrayListOfExpressions.get(1), tempClass), tempClass);
        } else if (expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
            OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) expression;
            returnPair = new Pair<>(normalizeSingleObjectSomeValuesFrom(objectSomeValuesFrom, tempClass), tempClass);
        }
        return returnPair;
    }

    private Set<OWLSubClassOfAxiom> normalizeSingleObjectSomeValuesFrom(OWLObjectSomeValuesFrom objectSomeValuesFrom, OWLClass tempClass) {
        Set<OWLSubClassOfAxiom> returnSet = new HashSet<>();
        OWLSubClassOfAxiom sub1 = this.df.getOWLSubClassOfAxiom(tempClass, objectSomeValuesFrom);
        OWLSubClassOfAxiom sub2 = this.df.getOWLSubClassOfAxiom(objectSomeValuesFrom, tempClass);
        returnSet.add(sub1);
        returnSet.add(sub2);
        return returnSet;
    }

    private Set<OWLSubClassOfAxiom> normalizeSingleIntersectionOf(OWLClassExpression prev,
                                                                  OWLClassExpression curr, OWLClass tempClass) {
        Set<OWLSubClassOfAxiom> returnSet = new HashSet<>();
        OWLSubClassOfAxiom sub1 = this.df.getOWLSubClassOfAxiom(tempClass, prev);
        OWLSubClassOfAxiom sub2 = this.df.getOWLSubClassOfAxiom(tempClass, curr);
        OWLObjectIntersectionOf intersectionPair = this.df.getOWLObjectIntersectionOf(prev, curr);
        OWLSubClassOfAxiom sub3 = this.df.getOWLSubClassOfAxiom(intersectionPair, tempClass);
        returnSet.add(sub1);
        returnSet.add(sub2);
        returnSet.add(sub3);
        return returnSet;
    }
}