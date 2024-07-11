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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MyReasoner {

    private final OWLDataFactory df;
    private int universalTempCount = 0;
    private Set<OWLSubClassOfAxiom> normalizedAxiomsSet = null;
    private Map<OWLClassExpression, Set<OWLClassExpression>> S = null;
    private Map<OWLObjectPropertyExpression, Set<Pair<OWLClassExpression, OWLClassExpression>>> R = null;

    /**
     * Sono inizializzati: <br>
     * - normalizedAxiomsSet con gli assiomi relativi alla tassonomia delle classi, escludendo quelli importati da ontologie esterne (Imports.EXCLUDED), <br>
     * - S e R come HashMap vuote
     **/
    public MyReasoner(OWLOntology o) {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        this.df = man.getOWLDataFactory();
        Set<OWLAxiom> subClassOfAxioms = o.getTBoxAxioms(Imports.EXCLUDED);
        this.normalizedAxiomsSet = normalization(subClassOfAxioms);
        this.S = new HashMap<>();
        this.R = new HashMap<>();
    }

    @SafeVarargs
    private final <T extends  OWLClassExpression> Set<T> createSet(T... items){
        return Stream.of(items).collect(Collectors.toSet());
    }

    private boolean hasAnyTrue(boolean... items){
        for(boolean item: items){
            if(item){
                return true;
            }
        }
        return false;
    }

    private <T extends OWLClassExpression> boolean isSomeValueFrom(T expression){
        return expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_SOME_VALUES_FROM);
    }

    private <T extends OWLClassExpression> boolean isIntersection(T expression){
        return expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_INTERSECTION_OF);
    }

    private <T extends OWLClassExpression> boolean isClass(T expression){
        return expression.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS);
    }

    private <T extends OWLClassExpression> boolean isIndividual(T expression){
        return expression.getClassExpressionType().equals(ClassExpressionType.OBJECT_ONE_OF);
    }

    /**
     * Verifica la validità di una query OWLSubClassOfAxiom rispetto agli assiomi già presenti nella base di conoscenza.
     * Il valore ritornato corrisponde alla presenza di un'assioma che indica che X è una sottoclasse di Y.
     *
     * @param query la query OWLSubClassOfAxiom su cui effettuare l'operazione. Deve specificare la sotto-classe e la super-classe.
     * @return true se la query è valida rispetto agli assiomi presenti, false altrimenti.
     **/
    public boolean doQuery(final OWLSubClassOfAxiom query) {
        Set<OWLSubClassOfAxiom> mergedSubAxiomsSet = new HashSet<>();

        Set<OWLAxiom> fictitiousSet = createFictitious(query.getSubClass(), query.getSuperClass());
        fictitiousSet.stream()
                .map(ax -> (OWLSubClassOfAxiom) ax)
                .forEach(cast -> {
                    OWLClassExpression subClass2 = cast.getSubClass();
                    OWLClassExpression superClass2 = cast.getSuperClass();
                    subAndSuperCheckBottom(subClass2, superClass2);
                });

        mergedSubAxiomsSet.addAll(this.normalizedAxiomsSet);
        mergedSubAxiomsSet.addAll(normalization(fictitiousSet));
        initializeMapping(mergedSubAxiomsSet);
        applyingCompletionRules(mergedSubAxiomsSet);

        return this.S.get(this.df.getOWLClass("#X"))
                .contains(this.df.getOWLClass("#Y"));
    }

    /**
     * Crea concetti finti (Fictitious) utili a dimostrare che subClass è sottoclasse di superClass
     * @return due assiomi: uno dimostra che x è sottoclasse di subClass, l'altro che y è superclasse di y
     **/
    private Set<OWLAxiom> createFictitious(final OWLClassExpression subClass, final OWLClassExpression superClass) {
        OWLClass x = this.df.getOWLClass(IRI.create("#X"));
        OWLClass y = this.df.getOWLClass(IRI.create("#Y"));

        return Stream.of(
                this.df.getOWLSubClassOfAxiom(x, subClass),
                this.df.getOWLSubClassOfAxiom(superClass, y)
        ).collect(Collectors.toSet());
    }


    /**
     * Applica a entrambe le classi di ciascun OWLSubClassOfAxiom dell'input la funzione initializeSingleMapping()
     * per memorizzare in this.S e this.R i concetti e relazioni associati.
     * Non apporta nessuna modifica sull'input
     **/
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
     **/
    private void initializeSingleMapping(final OWLClassExpression expression) {
        switch (expression.getClassExpressionType()) {
            case OWL_CLASS:
            case OBJECT_ONE_OF:
                S.put(expression, createSet(expression, df.getOWLThing()));
                break;
            case OBJECT_INTERSECTION_OF:
                OWLObjectIntersectionOf intersectionOf = (OWLObjectIntersectionOf) expression;
                ArrayList<OWLClassExpression> twoClasses = new ArrayList<>(intersectionOf.getOperandsAsList());
                S.put(twoClasses.get(0), createSet(twoClasses.get(0), this.df.getOWLThing()));
                S.put(twoClasses.get(1), createSet(twoClasses.get(1), this.df.getOWLThing()));
                break;
            case OBJECT_SOME_VALUES_FROM:
                OWLObjectSomeValuesFrom cast = (OWLObjectSomeValuesFrom) expression;
                R.put(cast.getProperty(), new HashSet<>());
                // Inserisco nella mappa S la classe (o singleton) dell'esistenziale e il setS creato per essa
                S.put(cast.getFiller(), createSet(cast.getFiller(), this.df.getOWLThing()));
                break;
        }
    }

    /**
     * Applica le regole di completamento su un insieme di assiomi di sottoclasse OWL per derivare implicitamente ulteriori assiomi deducibili.
     * Il metodo esegue iterativamente le regole di completamento finché nuovi assiomi possono essere dedotti.
     * Le regole di completamento applicate includono CR1, CR2, CR3 per le espressioni di classe e CR4, CR5, CR6 per le proprietà di oggetti.
     *
     * @param mergedSubClassAxioms l'insieme di assiomi di sottoclasse OWL su cui applicare le regole di completamento.
     **/
    private void applyingCompletionRules(Set<OWLSubClassOfAxiom> mergedSubClassAxioms) {
        boolean repeatLoop;

        do {
            boolean anyRuleChanged = false;

            for (OWLClassExpression key : this.S.keySet()) {
                anyRuleChanged |= hasAnyTrue(
                        CR1(key, mergedSubClassAxioms),
                        CR2(key, mergedSubClassAxioms),
                        CR3(key, mergedSubClassAxioms)
                );
            }

            for (OWLObjectPropertyExpression key : this.R.keySet()) {
                anyRuleChanged |= hasAnyTrue(
                        CR4(key, mergedSubClassAxioms),
                        CR5(key)
                );
            }

            DefaultDirectedGraph<OWLClassExpression, DefaultEdge> graphForCR6 = generateGraph();
            for (OWLClassExpression key1 : this.S.keySet()) {
                for (OWLClassExpression key2 : this.S.keySet()) {
                    anyRuleChanged |= CR6(key1, key2, graphForCR6);
                }
            }

            repeatLoop = anyRuleChanged;
        } while (repeatLoop);
    }

    /**
     * Applica la regola di completamento CR1 per l'aggiunta di nuove espressioni di classe all'insieme S(C) per una data espressione di classe C.
     * La regola controlla se è possibile aggiungere nuove espressioni di classe C' all'insieme S(C) in base alle sottoclassi dirette nell'ontologia.
     * Viene esclusa l'aggiunta di espressioni di classe specificate tramite restrizioni.
     *
     * @param key L'espressione di classe C su cui applicare la regola di completamento.
     * @param mergedSubClassAxioms L'insieme di assiomi di sottoclasse su cui basare il completamento.
     * @return true se è stata aggiunta almeno una nuova espressione di classe a S(C), altrimenti false.
     **/
    private boolean CR1(OWLClassExpression key, Set<OWLSubClassOfAxiom> mergedSubClassAxioms) {
        Set<OWLClassExpression> tempSet = new HashSet<>(this.S.get(key));
        boolean ret = false;

        for (OWLClassExpression setElementS : tempSet) { //Ciclo su ogni C' appartenente ad S(C)
            for (OWLSubClassOfAxiom sub : mergedSubClassAxioms) { //Ciclo su ogni sottoclasse della base di conoscenza
                OWLClassExpression subClass = sub.getSubClass();
                OWLClassExpression superClass = sub.getSuperClass();
                boolean subIsCurrent = subClass.equals(setElementS);

                if (!isSomeValueFrom(subClass) && subIsCurrent && !isSomeValueFrom(superClass)) {
                    if (this.S.get(key).add(superClass)){
                        ret = true;
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
     **/
    private boolean CR2(OWLClassExpression key, Set<OWLSubClassOfAxiom> mergedSubClassAxioms) {
        boolean checkAdd, ret = false;
        List<OWLClassExpression> listClass = new ArrayList<>(this.S.get(key));

        for (int i = 0; i < listClass.size(); i++)
            for (int j = i + 1; j < listClass.size(); j++) {
                OWLObjectIntersectionOf intersectionOf = this.df.getOWLObjectIntersectionOf(listClass.get(i), listClass.get(j));
                for (OWLSubClassOfAxiom ax : mergedSubClassAxioms) {
                    OWLClassExpression subClass = ax.getSubClass();
                    OWLClassExpression superClass = ax.getSuperClass();

                    if (isIntersection(subClass) && subClass.equals(intersectionOf) && !isSomeValueFrom(superClass)) {
                        if (this.S.get(key).add(superClass)){
                            ret = true;
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
     **/
    private boolean CR3(OWLClassExpression key, Set<OWLSubClassOfAxiom> mergedSubClassAxioms) {
        boolean ret = false;
        Set<OWLClassExpression> tempSet = new HashSet<>(this.S.get(key));

        for (OWLClassExpression setElementS : tempSet) { //Ciclo su ogni C' appartenente ad S(C)
            for (OWLSubClassOfAxiom sub : mergedSubClassAxioms) {
                OWLClassExpression subClass = sub.getSubClass();
                OWLClassExpression superClass = sub.getSuperClass();

                if (!isSomeValueFrom(subClass) && subClass.equals(setElementS) && isSomeValueFrom(superClass)) {
                    OWLObjectSomeValuesFrom castedSuperClass = (OWLObjectSomeValuesFrom) superClass;
                    OWLObjectPropertyExpression relation = castedSuperClass.getProperty();
                    if (this.R.get(relation).add(new Pair<>(key, castedSuperClass.getFiller()))){
                        ret = true;
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
     **/
    private boolean CR4(OWLObjectPropertyExpression key, Set<OWLSubClassOfAxiom> mergedSubClassAxioms){
        Set<Pair<OWLClassExpression,OWLClassExpression>> setOfPair = this.R.get(key);
        boolean ret = false;

        for(Pair<OWLClassExpression,OWLClassExpression> pair : setOfPair){ //Ciclo sul set di Pair
            OWLClassExpression leftOfPair = pair.getKey(); //Elemento sinistro del Pair (C)
            OWLClassExpression rightOfPair = pair.getValue(); //Elemento destro del Pair (D)

            for(OWLClassExpression expression : this.S.get(rightOfPair)){ //Ciclo sul Set di S(D) e ottengo expression = D'
                for(OWLSubClassOfAxiom subClassOfAxiom : mergedSubClassAxioms){ //Ciclo sugli assiomi di sussunzione normalizzati
                    if(isSomeValueFrom(subClassOfAxiom.getSubClass())){ //Verifico che lato sinistro sia esiste(r.K)
                        OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) subClassOfAxiom.getSubClass();
                        OWLObjectPropertyExpression relation = objectSomeValuesFrom.getProperty();
                        OWLClassExpression filler = objectSomeValuesFrom.getFiller(); //Prendo la parte interna dell'esistenziale (K)
                        if(relation.equals(key) && filler.equals(expression)) {
                            if (this.S.get(leftOfPair).add(subClassOfAxiom.getSuperClass())){ //Aggiungo a S(C) E
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
     **/
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
     **/
    private boolean CR6(OWLClassExpression key1, OWLClassExpression key2, DefaultDirectedGraph<OWLClassExpression, DefaultEdge> graph){
        if(!key1.equals(key2) && !key1.isOWLNothing()){
            Set<OWLClassExpression> intersectionSetKey1AndKey2 = new HashSet<>(this.S.get(key1));
            intersectionSetKey1AndKey2.retainAll(this.S.get(key2));

            for(OWLClassExpression expression : intersectionSetKey1AndKey2){
                if(isIndividual(expression)){
                    if(new DijkstraShortestPath<>(graph).getPath(key1,key2) != null){
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
     **/
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
     **/
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
     **/
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
     **/
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
            boolean subIsSomeValueORIntersection = isSomeValueFrom(leftPair.getValue()) || isIntersection(leftPair.getValue());

            if (subIsSomeValueORIntersection && isSomeValueFrom(rightPair.getValue())) {
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
     **/
    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> subClassNormalization(OWLClassExpression subClass) {
        switch (subClass.getClassExpressionType()) {
            case OWL_CLASS:
            case OBJECT_ONE_OF:
                return new Pair<>(new HashSet<>(), subClass);
            case OBJECT_INTERSECTION_OF:
                OWLObjectIntersectionOf intersectionOf = (OWLObjectIntersectionOf) subClass;
                return normalizeIntersectionOf(intersectionOf);
            case OBJECT_SOME_VALUES_FROM:
                OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) subClass;
                return normalizeObjectSomeValueFrom(objectSomeValuesFrom);
        }
        return null;
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
     **/
    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> superClassNormalization(OWLClassExpression superClass) {
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair;

        switch (superClass.getClassExpressionType()) {
            case OWL_CLASS:
            case OBJECT_ONE_OF:
                return new Pair<>(new HashSet<>(), superClass);
            case OBJECT_INTERSECTION_OF:
                OWLObjectIntersectionOf intersectionOf = (OWLObjectIntersectionOf) superClass;
                Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair = normalizeIntersectionOf(intersectionOf);
                returnPair = reduceToClass(tempPair.getValue());
                returnPair.getKey().addAll(tempPair.getKey());
                return returnPair;
            case OBJECT_SOME_VALUES_FROM:
                OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) superClass;
                return normalizeObjectSomeValueFrom(objectSomeValuesFrom);
        }
        return null;
    }

    private void normalizeInnerIntersectionOfInIntersectionOf(
            OWLClassExpression expression, Set<OWLSubClassOfAxiom> returnSet,
            ArrayList<OWLClassExpression> arrayListOfExpressions,
            int position){
        if (isSomeValueFrom(expression)) {
            OWLObjectSomeValuesFrom objectSomeValuesFrom = (OWLObjectSomeValuesFrom) expression;
            Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair = normalizeSomeValuesFromAsClass(objectSomeValuesFrom);
            returnSet.addAll(tempPair.getKey());
            arrayListOfExpressions.set(position, tempPair.getValue());
        }
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
     **/
    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> normalizeIntersectionOf(OWLObjectIntersectionOf intersectionOf) {
        ArrayList<OWLClassExpression> arrayListOfExpressions = new ArrayList<>(intersectionOf.getOperandsAsList());
        int size = arrayListOfExpressions.size();

        List<OWLClassExpression> setTempClasses = new ArrayList<>();
        Set<OWLSubClassOfAxiom> returnSet = new HashSet<>();
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair;

        if (size == 2) {
            normalizeInnerIntersectionOfInIntersectionOf(
                    arrayListOfExpressions.get(0),
                    returnSet,
                    arrayListOfExpressions,
                    0
            );
            normalizeInnerIntersectionOfInIntersectionOf(
                    arrayListOfExpressions.get(1),
                    returnSet,
                    arrayListOfExpressions,
                    1
            );
            OWLObjectIntersectionOf newIntersectionOf =
                    this.df.getOWLObjectIntersectionOf(arrayListOfExpressions.get(0), arrayListOfExpressions.get(1));
            return new Pair<>(returnSet, newIntersectionOf);
        }

        for (int i = 0; i < size; i++) {
            normalizeInnerIntersectionOfInIntersectionOf(
                    arrayListOfExpressions.get(i),
                    returnSet,
                    arrayListOfExpressions,
                    i
            );
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
     **/
    //TORNA ESISTENZIALE DI UNA CLASSE (Exist(r.C))
    private Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> normalizeObjectSomeValueFrom(OWLObjectSomeValuesFrom someValuesFrom) {
        OWLObjectPropertyExpression relation = someValuesFrom.getProperty();
        OWLClassExpression filler = someValuesFrom.getFiller();

        Set<OWLSubClassOfAxiom> returnSet = new HashSet<>();
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> tempPair;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> reduceToClassPair;
        Pair<Set<OWLSubClassOfAxiom>, OWLClassExpression> returnPair = null;

        boolean isFillerClassORIndividual = isClass(filler) || isIndividual(filler);
        if (isFillerClassORIndividual) {
            return new Pair<>(returnSet, someValuesFrom);
        } else if (isIntersection(filler)) {
            tempPair = normalizeIntersectionOf((OWLObjectIntersectionOf) filler); //Torna pair di set e un and singolo
            reduceToClassPair = reduceToClass(tempPair.getValue()); //Prende l'and singolo e lo riduce ad una classe TEMP
            reduceToClassPair.getKey().addAll((tempPair.getKey()));
            OWLObjectSomeValuesFrom normalizedSomeValuesFrom = this.df.getOWLObjectSomeValuesFrom(relation, reduceToClassPair.getValue());
            returnPair = new Pair<>(reduceToClassPair.getKey(), normalizedSomeValuesFrom);
        }
        else if (isSomeValueFrom(filler)) {
            //REMINDER: TORNARE ESISTENZIALE DI CLASSE (REDUCETOCLASSPAIR POTREBBE ESSERE VUOTO DOPO IF)
            tempPair = normalizeObjectSomeValueFrom((OWLObjectSomeValuesFrom) filler); //Torna un set e una classe temp o esistenziale
            OWLClassExpression expression = tempPair.getValue(); //Prendo l'espressione a destra della coppia (che è esistenziale di una classe)
            reduceToClassPair = new Pair<>(tempPair.getKey(), expression); //Inizializzo Pair con contenuto uguale a tempPair

            if (isSomeValueFrom(expression)) {
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
     **/
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
     **/
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
     **/
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
     **/
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