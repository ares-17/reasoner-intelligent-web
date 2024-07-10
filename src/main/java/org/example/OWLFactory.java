package org.example;

import org.semanticweb.owlapi.model.*;

import java.util.stream.Stream;

public class OWLFactory {

    private final IRI IOR;
    private final OWLDataFactory df;

    public OWLFactory(IRI iri, OWLDataFactory df){
        IOR = iri;
        this.df = df;
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
}
