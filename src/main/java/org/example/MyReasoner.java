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

    /**
     * Sono inzializzati: <br>
     * - normalizedAxiomsSet con gli assiomi relativi alla tassonomia delle classi, escludendo quelli importati da ontologie esterne (Imports.EXCLUDED), <br>
     * - S e R come HashMap vuote
     **/
    public MyReasoner(OWLOntology o) {
        this.ontology = o;
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        this.df = man.getOWLDataFactory();
        Set<OWLAxiom> subClassOfAxioms = this.ontology.getTBoxAxioms(Imports.EXCLUDED);
        this.normalizedAxiomsSet = normalization(subClassOfAxioms);
        this.S = new HashMap<>();
        this.R = new HashMap<>();
    }

    /**
     * Verifica la validità di una query OWLSubClassOfAxiom rispetto agli assiomi già presenti nella base di conoscenza.
     * Il valore ritornato corrisponde alla presenza di un'assioma che indica che FIT0 è una sottoclasse di FIT1.
     *
     * @param query la query OWLSubClassOfAxiom su cui effettuare l'operazione. Deve specificare la sotto-classe e la super-classe.
     * @return true se la query è valida rispetto agli assiomi presenti, false altrimenti.
     */
    public boolean doQuery(final OWLSubClassOfAxiom query) {
        Set<OWLSubClassOfAxiom> mergedSubAxiomsSet = new HashSet<>();

        OWLClassExpression subClass = query.getSubClass();
        OWLClassExpression superClass = query.getSuperClass();
        Set<OWLAxiom> fictitiousSet = createFictitious(subClass, superClass);
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

    /**
     * Crea concetti finti (Fictitious) utili a dimostrare che subClass è sottoclasse di superClass
     * @return due assiomi: uno dimostra che fit0 è sottoclasse di subClass, l'altro che fit1 è superclasse di fit1
     */
    private Set<OWLAxiom> createFictitious(final OWLClassExpression subClass, final OWLClassExpression superClass) {
        Set<OWLAxiom> returnSet = new HashSet<>();
        OWLClass fit0 = this.df.getOWLClass(IRI.create("#FIT0"));
        OWLClass fit1 = this.df.getOWLClass(IRI.create("#FIT1"));
        OWLSubClassOfAxiom sub1 = this.df.getOWLSubClassOfAxiom(fit0, subClass);
        OWLSubClassOfAxiom sub2 = this.df.getOWLSubClassOfAxiom(superClass, fit1);
        returnSet.add(sub1);
        returnSet.add(sub2);
        return returnSet;
    }


    /**
     * Applica a entrambe le classi di ciascun OWLSubClassOfAxiom dell'input la funzione initializeSingleMapping()
     * per memorizzare in this.S e this.R i concetti e relazioni associati.
     * Non apporta nessuna modifica sull'input
     */
    private void initializeMapping(final Set<OWLSubClassOfAxiom> normalizedAxSet) {
        for (OWLSubClassOfAxiom ax : normalizedAxSet) {
            OWLClassExpression subClass = ax.getSubClass();
            OWLClassExpression superClass = ax.getSuperClass();
            initializeSingleMapping(subClass);
            initializeSingleMapping(superClass);
        }
    }

    /**
     * Inizializza la mappatura dei concetti (S) e delle relazioni (R) per una singola espressione di classe OWL.
     * Se l'espressione è una classe OWL o un è un singleton, viene aggiunta al setS insieme alla classe di massimo livello OWLThing e viene inserita nella mappatura S.
     * Se l'espressione è un'intersezione di oggetti, le due classi che compongono l'intersezione vengono aggiunte al setS insieme alla classe di massimo livello OWLThing e ciascuna viene inserita nella mappatura S.
     * Se l'espressione è un'esistenza su valori di oggetto, viene creata un'associazione tra la proprietà e un insieme di coppie di espressioni di classe e viene aggiunta al setS la classe o il singleton dell'esistenziale, insieme alla classe di massimo livello OWLThing, quindi viene inserita nella mappatura S.
     *
     * @param expression l'espressione di classe OWL da inizializzare.
     */
    private void initializeSingleMapping(final OWLClassExpression expression) {
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

    /**
     * Applica le regole di completamento su un insieme di assiomi di sottoclasse OWL per derivare implicitamente ulteriori assiomi deducibili.
     * Il metodo esegue iterativamente le regole di completamento finché nuovi assiomi possono essere dedotti.
     * Le regole di completamento applicate includono CR1, CR2, CR3 per le espressioni di classe e CR4, CR5, CR6 per le proprietà di oggetti.
     *
     * @param mergedSubClassAxioms l'insieme di assiomi di sottoclasse OWL su cui applicare le regole di completamento.
     */
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

    /**
     * Applica la regola di completamento CR1 per l'aggiunta di nuove espressioni di classe all'insieme S(C) per una data espressione di classe C.
     * La regola controlla se è possibile aggiungere nuove espressioni di classe C' all'insieme S(C) in base alle sottoclassi dirette nell'ontologia.
     * Viene esclusa l'aggiunta di espressioni di classe specificate tramite restrizioni.
     *
     * @param key L'espressione di classe C su cui applicare la regola di completamento.
     * @param mergedSubClassAxioms L'insieme di assiomi di sottoclasse su cui basare il completamento.
     * @return true se è stata aggiunta almeno una nuova espressione di classe a S(C), altrimenti false.
     */
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

    /**
     * Applica la regola di completamento CR2 per l'aggiunta di nuove espressioni di classe all'insieme S(C) per una data espressione di classe C.
     * La regola controlla la possibilità di creare intersezioni tra le espressioni di classe esistenti in S(C) e aggiungere le espressioni di classe risultanti all'insieme.
     * Viene esclusa l'aggiunta di espressioni di classe specificate tramite restrizioni.
     *
     * @param key L'espressione di classe C su cui applicare la regola di completamento.
     * @param mergedSubClassAxioms L'insieme di assiomi di sottoclasse su cui basare il completamento.
     * @return true se è stata aggiunta almeno una nuova espressione di classe a S(C), altrimenti false.
     */
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

    /**
     * Applica la regola di completamento CR3 per l'aggiunta di nuove coppie di espressioni di classe e filler all'insieme R per una data espressione di classe C.
     * La regola esamina le sottoclassi presenti in S(C) e controlla se una di esse corrisponde alla classe soggetto di un'espressione di valore esistenziale.
     * In tal caso, viene aggiunta una nuova coppia (espressione di classe, filler) all'insieme R corrispondente alla proprietà dell'espressione di valore esistenziale.
     *
     * @param key L'espressione di classe C su cui applicare la regola di completamento.
     * @param mergedSubClassAxioms L'insieme di assiomi di sottoclasse su cui basare il completamento.
     * @return true se è stata aggiunta almeno una nuova coppia (espressione di classe, filler) all'insieme R, altrimenti false.
     */
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

    /**
     * Applica la regola di completamento CR4 per l'aggiunta di nuove espressioni di classe all'insieme S(C) per una data espressione di proprietà.
     * La regola esamina le coppie (C, D) presenti nell'insieme R relativo alla proprietà data e per ciascuna coppia controlla se esiste un'espressione di classe D'
     * in S(D) tale che esiste una sottoclasse di D' che corrisponde alla classe soggetto di un'espressione di valore esistenziale avente come proprietà la stessa proprietà data.
     * In tal caso, viene aggiunta una nuova espressione di classe all'insieme S(C) corrispondente alla classe soggetto della sussunzione.
     *
     * @param key L'espressione di proprietà OWLObjectPropertyExpression su cui applicare la regola di completamento.
     * @param mergedSubClassAxioms L'insieme di assiomi di sottoclasse su cui basare il completamento.
     * @return true se è stata aggiunta almeno una nuova espressione di classe all'insieme S(C), altrimenti false.
     */
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

    /**
     * Applica la regola di completamento CR5 per l'aggiunta del concetto di Bottom all'insieme S(C) per una data espressione di proprietà.
     * La regola esamina le coppie (C, D) presenti nell'insieme R relativo alla proprietà data e per ciascuna coppia verifica se esiste un'espressione di classe D'
     * in S(D) tale che D' corrisponda al concetto di Bottom. In tal caso, viene aggiunto il concetto di Bottom all'insieme S(C) corrispondente alla classe soggetto della coppia.
     *
     * @param key L'espressione di proprietà OWLObjectPropertyExpression su cui applicare la regola di completamento.
     * @return true se è stata aggiunta almeno una nuova espressione di classe Bottom all'insieme S(C), altrimenti false.
     */
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

    /**
     * Applica la regola di completamento CR6 per l'aggiunta di espressioni di classe all'insieme S(C) per due date espressioni di classe.
     * La regola esamina le due espressioni di classe key1 e key2 e verifica se hanno un'intersezione non vuota. Se sì, controlla se esiste un percorso nel grafo fornito
     * tra key1 e key2. Se esiste un percorso, aggiunge tutte le espressioni di classe di key2 all'insieme S(key1).
     *
     * @param key1 L'espressione di classe OWLClassExpression per la quale applicare la regola di completamento CR6.
     * @param key2 L'espressione di classe OWLClassExpression da cui aggiungere le espressioni di classe all'insieme S(key1).
     * @param graph Il grafo diretto utilizzato per verificare l'esistenza di un percorso tra key1 e key2.
     * @return true se sono state aggiunte nuove espressioni di classe all'insieme S(key1), altrimenti false.
     */
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

    /**
     * Viene definito un grafo diretto con gli elementi degli insiemi this.S e this.R
     */
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

    /**
     * Verifica se un'espressione di classe contiene l'entità "bottom" in una posizione non consentita.
     * L'entità "bottom" rappresenta l'insieme vuoto o un concetto inconsistente nell'ontologia OWL.
     * Questo metodo controlla ricorsivamente tutte le espressioni di classe annidate all'interno dell'espressione data.
     * Se viene trovata un'espressione di classe che corrisponde all'entità "bottom", viene sollevata un'eccezione IllegalArgumentException
     * con un messaggio che indica la presenza di "bottom" in una posizione non consentita.
     *
     * @param expression L'espressione di classe da controllare per la presenza di "bottom".
     * @throws IllegalArgumentException Se viene trovato "bottom" in una posizione non consentita.
     */
    private void checkBottom(final OWLClassExpression expression) {
        Set<OWLClassExpression> set = expression.getNestedClassExpressions();
        for (OWLClassExpression ex : set) {
            if (ex.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS)) {
                OWLClass cl = (OWLClass) ex;
                if (cl.isBottomEntity()) {
                    throw new IllegalArgumentException("Trovato bottom in posizione non consentita");
                }
            }
        }
    }

    /**
     * Applica checkBottom a subClass e anche a superClass se non è di tipo ClassExpressionType.OWL_CLASS
     */
    private void subAndSuperCheckBottom(OWLClassExpression subClass, OWLClassExpression superClass) {
        checkBottom(subClass);
        if (!superClass.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS)) {
            checkBottom(superClass);
        }
    }

    /**
     * Normalizza gli assiomi di sussunzione forniti.
     * Questo metodo normalizza gli assiomi di sussunzione dati in input. Per ciascun assioma di sussunzione,
     * controlla se le espressioni di classe che lo compongono contengono l'entità "bottom" in posizioni non consentite.
     * Successivamente, normalizza sia la sotto-classe che la super-classe dell'assioma di sussunzione.
     * Se le espressioni di classe risultanti soddisfano determinate condizioni, vengono ridotte a una singola espressione di classe.
     * Infine, viene creato un nuovo assioma di sussunzione normalizzato e aggiunto all'insieme di assiomi normalizzati.
     *
     * @param subClassOfAxioms L'insieme di assiomi di sussunzione da normalizzare.
     * @return Un insieme di assiomi di sussunzione normalizzati.
     */
    private Set<OWLSubClassOfAxiom> normalization(final Set<OWLAxiom> subClassOfAxioms) {
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


    /**
     * Normalizza un'espressione di classe di sottoclasse.
     * Questo metodo prende un'espressione di classe di sottoclasse e determina il suo tipo. Se l'espressione di classe è
     * una semplice classe OWL o un oggetto OWL one of, crea una coppia contenente un insieme vuoto di assiomi di sottoclasse e l'espressione di classe originale stessa.
     * Se l'espressione di classe è un'intersezione di espressioni di classe, delega il processo di normalizzazione
     * a un altro metodo chiamato `normalizeIntersectionOf`, passando l'espressione di intersezione come parametro.
     * Se l'espressione di classe è una restrizione esistenziale (some values from expression), delega il
     * processo di normalizzazione a un altro metodo chiamato `normalizeObjectSomeValueFrom`, passando la restrizione esistenziale
     * come parametro.
     * @param subClass L'espressione di classe di sottoclasse da normalizzare.
     * @return Una coppia contenente un insieme di assiomi di sottoclasse normalizzati e l'espressione di classe normalizzata.
     */
    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> subClassNormalization(OWLClassExpression subClass) {
        Set<OWLSubClassOfAxiom> set = new HashSet<>();
        ClassExpressionType typeSubClass = subClass.getClassExpressionType();
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair = null;

        if (typeSubClass.equals(ClassExpressionType.OWL_CLASS) || typeSubClass.equals(ClassExpressionType.OBJECT_ONE_OF)) { //Verifica se è una classe semplice
            returnPair = new Pair<>(set, subClass);
        } else if (typeSubClass.equals(ClassExpressionType.OBJECT_INTERSECTION_OF)) { //Verifica se è intersezione
            OWLObjectIntersectionOf intersectionOf = (OWLObjectIntersectionOf) subClass;
            returnPair = normalizeIntersectionOf(intersectionOf); //Il pair è ritornato dalla funzione chiamata
        } else if (typeSubClass.equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM)) {
            OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) subClass;
            returnPair = normalizeObjectSomeValueFrom(objectSomeValuesFrom);
        }
        return returnPair;
    }

    /**
     * Normalizza un'espressione di classe di sottoclasse.
     * Questo metodo prende un'espressione di classe di sottoclasse e determina il suo tipo. Se l'espressione di classe è
     * una semplice classe OWL o un oggetto OWL one of, crea una coppia contenente un insieme vuoto di assiomi di sottoclasse e l'espressione di classe originale stessa.
     * Se l'espressione di classe è un'intersezione di espressioni di classe, delega il processo di normalizzazione
     * a un altro metodo chiamato `normalizeIntersectionOf`, passando l'espressione di intersezione come parametro.
     * Se l'espressione di classe è una restrizione esistenziale (some values from expression), delega il
     * processo di normalizzazione a un altro metodo chiamato `normalizeObjectSomeValueFrom`, passando la restrizione esistenziale
     * come parametro.
     * @param superClass L'espressione di classe di sottoclasse da normalizzare.
     * @return Una coppia contenente un insieme di assiomi di sottoclasse normalizzati e l'espressione di classe normalizzata.
     */
    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> superClassNormalization(OWLClassExpression superClass) {
        Set<OWLSubClassOfAxiom> set = new HashSet<>();
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair = null;

        if (superClass.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS) ||
                superClass.getClassExpressionType().equals(ClassExpressionType.OBJECT_ONE_OF)) { //Verifica se è una classe semplice
            returnPair = new Pair<>(set, superClass);
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


    /**
     * Normalizza un'espressione di intersezione di classi.
     * <p>
     * Questo metodo prende un'espressione di intersezione di classi e la normalizza. Se l'intersezione contiene solo due
     * espressioni di classe e almeno una di esse è una restrizione esistenziale (some values from), il metodo normalizza
     * le restrizioni esistenziali e restituisce una nuova coppia di assiomi di sottoclasse contenente le espressioni di
     * classe normalizzate.
     * <p>
     * Se l'intersezione contiene più di due espressioni di classe, il metodo itera su di esse. Se una delle espressioni
     * è una restrizione esistenziale, la normalizza e aggiunge gli assiomi di sottoclasse generati al set di ritorno.
     * Se l'indice della classe nell'intersezione è dispari, il metodo crea una classe temporanea e normalizza
     * l'intersezione tra la classe precedente e quella corrente, aggiungendo gli assiomi di sottoclasse al set di ritorno.
     * Se l'indice è pari, il metodo aggiunge semplicemente la classe alla lista temporanea per la creazione di intersezioni
     * per la chiamata ricorsiva.
     * <p>
     * Se l'intersezione ha un numero dispari di espressioni di classe, il metodo crea un'intersezione ricorsiva delle classi
     * nella lista temporanea e chiama se stesso ricorsivamente per normalizzarla. Infine, restituisce una coppia
     * contenente gli assiomi di sottoclasse generati e l'intersezione normalizzata.
     *
     * @param intersectionOf L'intersezione di espressioni di classe da normalizzare.
     * @return Una coppia contenente un insieme di assiomi di sottoclasse normalizzati e l'intersezione normalizzata.
     */
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

    /**
     * Normalizza un'espressione di restrizione esistenziale (some values from).
     * <p>
     * Questo metodo prende un'espressione di restrizione esistenziale (some values from) e la normalizza. Se la filler
     * dell'espressione è una classe semplice o un singleton, restituisce una coppia di assiomi di sottoclasse vuota
     * e l'espressione di restrizione esistenziale stessa.
     * <p>
     * Se il filler dell'espressione è un'intersezione di classi, il metodo chiama il metodo normalizeIntersectionOf
     * per normalizzare l'intersezione. Quindi, riduce l'intersezione ad una singola classe e genera una nuova
     * restrizione esistenziale con la stessa relazione e la classe ridotta. Restituisce una coppia contenente gli assiomi
     * di sottoclasse generati e la nuova restrizione esistenziale normalizzata.
     * <p>
     * Se il filler dell'espressione è un'altra restrizione esistenziale, il metodo chiama se stesso ricorsivamente per
     * normalizzare la restrizione esistenziale interna. Quindi, genera una nuova restrizione esistenziale con la stessa
     * relazione e la classe normalizzata. Se la classe normalizzata è ancora una restrizione esistenziale, riduce la
     * restrizione ad una singola classe temporanea. Restituisce una coppia contenente gli assiomi di sottoclasse generati
     * e la nuova restrizione esistenziale normalizzata.
     * <p>
     * @param someValuesFrom La restrizione esistenziale da normalizzare.
     * @return Una coppia contenente un insieme di assiomi di sottoclasse normalizzati e la restrizione esistenziale
     *         normalizzata.
     */
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

    /**
     * Normalizza una restrizione esistenziale come una classe.
     *
     * Questo metodo prende un'espressione di restrizione esistenziale (some values from) e la normalizza come una classe.
     * Inizialmente, chiama il metodo normalizeObjectSomeValueFrom per normalizzare l'espressione esistenziale, ottenendo
     * una coppia di assiomi di sottoclasse e l'espressione esistenziale normalizzata. Gli assiomi generati durante la
     * normalizzazione vengono aggiunti a un set globale di assiomi. Successivamente, l'esistenziale normalizzato viene
     * ridotto ad una singola classe chiamando il metodo reduceToClass. Gli assiomi generati durante la riduzione a classe
     * vengono aggiunti al set globale di assiomi. Infine, restituisce una coppia contenente il set di assiomi generati
     * durante la normalizzazione e l'espressione esistenziale ridotta a classe.
     *
     * @param objectSomeValuesFrom La restrizione esistenziale da normalizzare come classe.
     * @return Una coppia contenente un insieme di assiomi di sottoclasse generati durante la normalizzazione e
     *         l'espressione esistenziale ridotta a classe.
     */
    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> normalizeSomeValuesFromAsClass(OWLObjectSomeValuesFrom objectSomeValuesFrom) {
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair = null;

        tempPair = normalizeObjectSomeValueFrom(objectSomeValuesFrom); //Norm. Exist. torna Pair di assiomi e esistenziale (Exist(r.C))
        Set<OWLSubClassOfAxiom> returnSet = new HashSet<>(tempPair.getKey()); //Aggiungo gli assiomi generati durante la normalizzazione al set di assiomi globale
        tempPair = reduceToClass(tempPair.getValue()); //Riduco a classe l'esistenziale attuale (perché siamo in una serie di and)
        returnSet.addAll(tempPair.getKey()); //Aggiungo gli assiomi generati durante la riduzione a classe dell'esistenziale

        return new Pair<>(returnSet, tempPair.getValue());
    }

    /**
     * Riduce un'espressione complessa a una singola classe.
     * <p>
     * Questo metodo prende un'espressione di classe complessa e la riduce a una singola classe.
     * Se l'espressione è un'intersezione di classi, viene decomposta e le classi risultanti vengono normalizzate come
     * espressioni singole utilizzando il metodo normalizeSingleIntersectionOf. Se l'espressione è una restrizione
     * esistenziale (some values from), viene normalizzata come una singola classe utilizzando il metodo
     * normalizeSingleObjectSomeValuesFrom. Infine, restituisce una coppia contenente un insieme di assiomi di
     * sottoclasse generati durante la normalizzazione e l'espressione ridotta a una singola classe.
     *
     * @param expression L'espressione complessa da ridurre a una singola classe.
     * @return Una coppia contenente un insieme di assiomi di sottoclasse generati durante la normalizzazione e
     *         l'espressione ridotta a una singola classe.
     */
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

    /**
     * Normalizza una restrizione esistenziale (some values from) come una singola classe.
     * <p>
     * Questo metodo prende una restrizione esistenziale (some values from) e la normalizza come una singola classe.
     * Genera due assiomi di sottoclasse: uno che afferma che la classe temporanea è una sottoclasse della restrizione
     * esistenziale, e l'altro che afferma che la restrizione esistenziale è una sottoclasse della classe temporanea.
     * Questi due assiomi consentono di trattare la restrizione esistenziale come una singola classe nel contesto di
     * un'espressione più complessa.
     *
     * @param objectSomeValuesFrom La restrizione esistenziale (some values from) da normalizzare.
     * @param tempClass La classe temporanea utilizzata per la normalizzazione.
     * @return Un insieme di assiomi di sottoclasse generati durante la normalizzazione.
     */
    private Set<OWLSubClassOfAxiom> normalizeSingleObjectSomeValuesFrom(OWLObjectSomeValuesFrom objectSomeValuesFrom, OWLClass tempClass) {
        Set<OWLSubClassOfAxiom> returnSet = new HashSet<>();
        OWLSubClassOfAxiom sub1 = this.df.getOWLSubClassOfAxiom(tempClass, objectSomeValuesFrom);
        OWLSubClassOfAxiom sub2 = this.df.getOWLSubClassOfAxiom(objectSomeValuesFrom, tempClass);
        returnSet.add(sub1);
        returnSet.add(sub2);
        return returnSet;
    }

    /**
     * Normalizza un'intersezione di classi come una singola classe.
     * <p>
     * Questo metodo prende due espressioni di classe e una classe temporanea e normalizza l'intersezione delle due classi come
     * una singola classe. Genera tre assiomi di sottoclasse: due che affermano che la classe temporanea è una sottoclasse
     * delle due espressioni di classe fornite, e uno che afferma che l'intersezione delle due classi è una sottoclasse della
     * classe temporanea. Questi assiomi consentono di trattare l'intersezione delle classi come una singola classe nel
     * contesto di un'espressione più complessa.
     *
     * @param prev L'espressione di classe precedente nell'intersezione.
     * @param curr L'espressione di classe corrente nell'intersezione.
     * @param tempClass La classe temporanea utilizzata per la normalizzazione.
     * @return Un insieme di assiomi di sottoclasse generati durante la normalizzazione.
     */
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