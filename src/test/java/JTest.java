import org.example.MyReasoner;
import org.example.OWLFactory;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import java.io.File;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class JTest {

    OWLReasoner hermitReasoner;
    private MyReasoner reasoner;
    private OWLDataFactory df;
    private IRI IOR;
    private OWLFactory of;

    @Before
    public void setUp() throws Exception {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology o = man.loadOntologyFromOntologyDocument(new File("data/FOOTBALL_ONTOLOGY.rdf"));
        this.df = man.getOWLDataFactory();
        this.IOR = o.getOntologyID().getOntologyIRI().get();
        this.of = new OWLFactory(this.IOR, this.df);
        this.reasoner = new MyReasoner(o);
        OWLReasonerFactory rf = new ReasonerFactory();
        this.hermitReasoner = rf.createReasoner(o);
        hermitReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
    }

    /**
     * Test to check if the intersection of GiocatoreTitolare and GiocatorePanchina is a subclass of Giocatore.
     * <pre>
     * Query in OWL2:
     * GiocatoreTitolare ⊓ GiocatorePanchina ⊑ Giocatore
     * </pre>
     */
    @Test
    public void GIOCATORETITOLARE_AND_GIOCATOREPANCHINA_SUBCLASS_OF_GIOCATORE() {
        OWLSubClassOfAxiom query = of.subclassOf(
                of.intersectionOf(
                        of.clazz("GiocatoreTitolare"),
                        of.clazz("GiocatorePanchina")),
                of.clazz("Giocatore"));

        assertTrue(reasoner.doQuery(query));
    }

    /**
     * Test to check if the intersection of GiocatoreTitolare and GiocatorePanchina is a subclass of the top concept (Thing).
     * <pre>
     * Query in OWL2:
     * GiocatoreTitolare ⊓ GiocatorePanchina ⊑ ⊤
     * </pre>
     */
    @Test
    public void GIOCATORETITOLARE_AND_GIOCATOREPANCHINA_SUBCLASS_OF_TOP() {
        OWLSubClassOfAxiom query = of.subclassOf(
                of.intersectionOf(
                        of.clazz("GiocatoreTitolare"),
                        of.clazz("GiocatorePanchina")),
                of.thing());

        assertTrue(reasoner.doQuery(query));
    }

    /**
     * Test to check if the top concept (Thing) is a subclass of the intersection of GiocatoreTitolare and GiocatorePanchina.
     * <pre>
     * Query in OWL2:
     * ⊤ ⊑ GiocatoreTitolare ⊓ GiocatorePanchina
     * </pre>
     */
    @Test
    public void TOP_SUBCLASS_OF_GIOCATORETITOLARE_AND_GIOCATOREPANCHINA() {
        OWLSubClassOfAxiom query = of.subclassOf(
                of.thing(),
                of.intersectionOf(
                        of.clazz("GiocatoreTitolare"),
                        of.clazz("GiocatorePanchina")
                ));

        assertFalse(reasoner.doQuery(query));
    }

    /**
     * Test to check if the intersection of Preparatore, Medico, Dirigente, and Adulto is a subclass of Persona.
     * <pre>
     * Query in OWL2:
     * Preparatore ⊓ Medico ⊓ Dirigente ⊓ Adulto ⊑ Persona
     * </pre>
     */
    @Test
    public void PREPARATORE_AND_MEDICO_AND_DIRIGENTE_AND_ADULTO_SUBCLASS_OF_PERSONA() {
        OWLSubClassOfAxiom query = of.subclassOf(
                of.intersectionOf(
                        of.clazz("Preparatore"),
                        of.clazz("Medico"),
                        of.clazz("Dirigente"),
                        of.clazz("Adulto")),
                of.clazz("Persona"));

        assertTrue(reasoner.doQuery(query));
    }

    @Test
    public void COACH_SUBCLASS_OF_ISCRITTO_A_PREMIER_LEAGUE() {
        OWLClass classF = df.getOWLClass(IOR + "#Coach");
        OWLClass classI = df.getOWLClass(IOR + "#PremierLeague");
        OWLObjectProperty r3 = df.getOWLObjectProperty(IOR + "#iscrittoA");
        OWLObjectSomeValuesFrom objectSomeValuesFromR3 = df.getOWLObjectSomeValuesFrom(r3, classI);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(classF, objectSomeValuesFromR3);
        assertFalse(reasoner.doQuery(query));
    }

    @Test
    public void GIOCATORE_PANCHINA_SUBCLASS_OF_HA_UN_CONTRATTO() {
        OWLClass classF = df.getOWLClass(IOR + "#GiocatorePanchina");
        OWLClass classI = df.getOWLClass(IOR + "#Contratto");
        OWLObjectProperty r3 = df.getOWLObjectProperty(IOR + "#haContratto");
        OWLObjectSomeValuesFrom objectSomeValuesFromR3 = df.getOWLObjectSomeValuesFrom(r3, classI);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(classF, objectSomeValuesFromR3);
        assertTrue(reasoner.doQuery(query));
    }

    @Test
    public void GIOCATORE_PANCHINA_SUBCLASS_OF_HA_UNA_SQUADRA_DIRIGENTE() {
        OWLClass classF = df.getOWLClass(IOR + "#GiocatorePanchina");
        OWLClass classI = df.getOWLClass(IOR + "#Dirigente");
        OWLObjectProperty r3 = df.getOWLObjectProperty(IOR + "#haSquadra");
        OWLObjectSomeValuesFrom objectSomeValuesFromR3 = df.getOWLObjectSomeValuesFrom(r3, classI);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(classF, objectSomeValuesFromR3);
        assertFalse(reasoner.doQuery(query));
    }

    @Test
    public void SQUADRA_SUBCLASS_OF_INDIVIDUAL_CAPO_MEDICO_COACH_CAPO_PREPARATORE() {
        //Here
        OWLClass classF = df.getOWLClass(IOR + "#Squadra");
        OWLObjectProperty r = df.getOWLObjectProperty(IOR + "#haCapoMedico");
        OWLObjectProperty r2 = df.getOWLObjectProperty(IOR + "#haCoach");
        OWLObjectProperty r3 = df.getOWLObjectProperty(IOR + "#haCapoPreparatore");
        OWLClass classA = df.getOWLClass(IOR + "#Medico");
        OWLClass classB = df.getOWLClass(IOR + "#Coach");
        OWLClass classG = df.getOWLClass(IOR + "#CapoPreparatore");
        OWLObjectSomeValuesFrom objectSomeValuesFromR = df.getOWLObjectSomeValuesFrom(r, classA);
        OWLObjectSomeValuesFrom objectSomeValuesFromR2 = df.getOWLObjectSomeValuesFrom(r2, classB);
        OWLObjectSomeValuesFrom objectSomeValuesFromR3 = df.getOWLObjectSomeValuesFrom(r3, classG);
        OWLObjectIntersectionOf intersectionOfSub = df.getOWLObjectIntersectionOf(objectSomeValuesFromR, objectSomeValuesFromR2, objectSomeValuesFromR3);
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(classF, intersectionOfSub);
        assertTrue(reasoner.doQuery(query));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testException() {
        OWLClass classA = df.getOWLClass(IOR + "#A");
        OWLSubClassOfAxiom query = df.getOWLSubClassOfAxiom(classA, df.getOWLNothing());
        reasoner.doQuery(query);
    }

}
