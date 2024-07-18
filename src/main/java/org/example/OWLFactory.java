package org.example;

import org.semanticweb.owlapi.model.*;

public class OWLFactory {

    private final IRI IOR;
    private final OWLDataFactory df;

    public OWLFactory(OWLOntologyManager man, OWLOntology o) {
        this.df = man.getOWLDataFactory();
        this.IOR = o.getOntologyID().getOntologyIRI().orElseThrow(IllegalArgumentException::new);
    }

    public OWLNamedIndividual individual(String name){
        return this.df.getOWLNamedIndividual(this.IOR + "#" + name);
    }

    public OWLObjectOneOf oneOf(OWLIndividual ...individual){
        return this.df.getOWLObjectOneOf(individual);
    }

    public OWLClass clazz(String name){
        return this.df.getOWLClass(this.IOR + "#" + name);
    }

    public OWLObjectIntersectionOf intersectionOf(OWLClassExpression... operands){
        return df.getOWLObjectIntersectionOf(operands);
    }

    public OWLSubClassOfAxiom subclassOf(OWLClassExpression subClass, OWLClassExpression superClass){
        return this.df.getOWLSubClassOfAxiom(subClass, superClass);
    }

    public OWLClass thing(){
        return df.getOWLThing();
    }

    public OWLClass nothing(){
        return df.getOWLNothing();
    }

    public OWLObjectPropertyExpression property(String name){
        return this.df.getOWLObjectProperty(this.IOR + "#" + name);
    }

    public OWLObjectSomeValuesFrom someValuesFrom(OWLObjectPropertyExpression property, OWLClassExpression clazz){
        return this.df.getOWLObjectSomeValuesFrom(property, clazz);
    }
}
