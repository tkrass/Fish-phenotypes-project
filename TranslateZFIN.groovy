@Grab(group='org.semanticweb.elk', module='elk-owlapi-standalone', version='0.4.2')

import java.util.logging.Logger
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary

PrintWriter fout = new PrintWriter(new FileWriter("ZFIN.txt"))

def ontfile = new File("ext.obo")
def uberon2zfa = [:]
def id = ""
ontfile.eachLine { line ->
  if (line.startsWith("id:")) {
    id = line.substring(3).trim()
  }
  if (line.startsWith("xref: ZFA:")) {
    uberon2zfa[id] = line.substring(5).trim()
  }
}

OWLOntologyManager manager = OWLManager.createOWLOntologyManager()

OWLDataFactory fac = manager.getOWLDataFactory()
OWLDataFactory factory = fac

println "Loading ontology file..."
OWLOntology ont = manager.loadOntologyFromOntologyDocument(new File("t1.owl"))
println "Ontology file loaded..."

OWLReasonerFactory reasonerFactory = null

//ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor()
//OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor)

println "Classifying ontology..."
OWLReasonerFactory f1 = new ElkReasonerFactory()
OWLReasoner reasoner = f1.createReasoner(ont)
println "Ontology classified..."

def r = { String s ->
  if (s == "part-of") {
    factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"))
  } else if (s == "has-part") {
    factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000051"))
  } else if (s == "inheres-in") {
    factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000052"))
  } else {
    factory.getOWLObjectProperty(IRI.create("http://phenomebrowser.net/#"+s))
  }
}

def c = { String s ->
  factory.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/"+s))
}

println "Processing file..."
def lineno=0
def lines=0
println "Counting lines..."
new File(args[0]).splitEachLine("\t") { line ->
  if(!line[0].startsWith("Taxon 1")){
    lines=lines+1
  }
}
println "Lines counted..."
new File(args[0]).splitEachLine("\t") { line ->
  if (!line[0].startsWith("Taxon 1")) {
    lineno=lineno+1
    println lineno+" out of "+lines
    def gene1=line[0]
	def gene2=line[2]
	def gene3=line[4]
    def e1 = line[10]?.replaceAll(":","_").split(" ")[0]
    //    def e2 = line[3]?.replaceAll(":","_").split(" ")[0]
    def p = line[12]?.replaceAll(":","_")
    if (e1 && p) {
      def cl = fac.getOWLObjectSomeValuesFrom(r("has-part"), fac.getOWLObjectIntersectionOf(c(e1),fac.getOWLObjectSomeValuesFrom(r("has-quality"),c(p))))
      def equivs = reasoner.getEquivalentClasses(cl).getEntities()
      if (equivs.size() == 0) {
	println "Cannot find equivalent class for " + gene1+" "+e1+" "+p+". Looking for superclasses instead..."
	reasoner.getSuperClasses(cl, true).getFlattened().each { sup ->
	  fout.println("$gene1\t$gene2\t$gene3\t"+sup)
	}
      }
      equivs.each { sup ->
	fout.println("$gene1\t$gene2\t$gene3\t"+sup)
      }
      //      reasoner.getSuperClasses(cl, true).each { sup ->
      //	fout.println("$taxon\t"+sup)
      //      }
    }
  }
}
fout.flush()
fout.close()
