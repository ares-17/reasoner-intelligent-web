import org.example.MyReasoner;
import org.example.OWLFactory;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JTest {

    private MyReasoner reasoner;
    private OWLFactory of;

    @Before
    public void setUp() throws Exception {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLOntology o = man.loadOntologyFromOntologyDocument(new File("data/FOOTBALL_ONTOLOGY.rdf"));
        this.of = new OWLFactory(man, o);
        this.reasoner = new MyReasoner(o);
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

    /**
     * Test to check if Coach is subclass of Iscritto a Premier League.
     * <pre>
     * Query in OWL2:
     * Coach ⊑ ∃ iscrittoA.PremierLeague
     * </pre>
     */
    @Test
    public void COACH_SUBCLASS_OF_ISCRITTO_A_PREMIER_LEAGUE() {
        OWLSubClassOfAxiom query = of.subclassOf(
                of.clazz("Coach"),
                of.someValuesFrom(
                        of.property("iscrittoA"),
                        of.clazz("PremierLeague")
                )
        );
        assertFalse(reasoner.doQuery(query));
    }

    /**
     * Test to check if GiocatorePanchina is subclass of Ha un Contratto.
     * <pre>
     * Query in OWL2:
     * GiocatorePanchina ⊑ ∃ haContratto.Contratto
     * </pre>
     */
    @Test
    public void GIOCATORE_PANCHINA_SUBCLASS_OF_HA_UN_CONTRATTO() {
        OWLSubClassOfAxiom query = of.subclassOf(
                of.clazz("GiocatorePanchina"),
                of.someValuesFrom(
                        of.property("haContratto"),
                        of.clazz("Contratto")
                )
        );
        assertTrue(reasoner.doQuery(query));
    }

    /**
     * Test to check if GiocatorePanchina is subclass of Ha una Squadra Dirigente.
     * <pre>
     * Query in OWL2:
     * GiocatorePanchina ⊑ ∃ haSquadra.Dirigente
     * </pre>
     */
    @Test
    public void GIOCATORE_PANCHINA_SUBCLASS_OF_HA_UNA_SQUADRA_DIRIGENTE() {
        OWLSubClassOfAxiom query = of.subclassOf(
                of.clazz("GiocatorePanchina"),
                of.someValuesFrom(
                        of.property("haSquadra"),
                        of.clazz("Dirigente")
                )
        );

        assertFalse(reasoner.doQuery(query));
    }

    /**
     * Test to check if Squadra is subclass of Individual CapoMedico and Coach and CapoPreparatore.
     * <pre>
     * Query in OWL2:
     * Squadra ⊑ (haCapoMedico some Medico) ⊓ (haCoach some Coach) ⊓ (haCapoPreparatore some CapoPreparatore)
     * </pre>
     */
    @Test
    public void SQUADRA_SUBCLASS_OF_INDIVIDUAL_CAPO_MEDICO_COACH_CAPO_PREPARATORE() {
        OWLSubClassOfAxiom query = of.subclassOf(
                of.clazz("Squadra"),
                of.intersectionOf(
                        of.someValuesFrom(of.property("haCapoMedico"), of.clazz("Medico")),
                        of.someValuesFrom(of.property("haCoach"), of.clazz("Coach")),
                        of.someValuesFrom(of.property("haCapoPreparatore"), of.clazz("CapoPreparatore"))
                )
        );

        assertTrue(reasoner.doQuery(query));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testException() {
        OWLSubClassOfAxiom query = of.subclassOf(of.clazz("A"), of.nothing());
        reasoner.doQuery(query);
    }

}
