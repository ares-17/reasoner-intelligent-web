package reasoner;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class Main {
	public static void main(String[] args) {
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		System.out.println(man);
	}
}
