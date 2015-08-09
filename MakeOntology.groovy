@Grapes([
	  @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='4.0.1'),
	  @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.1'),
	  @GrabConfig(systemClassLoader=true)
	])

import java.util.logging.Logger
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary

def ontfile = new File("uberon-ext.obo")
def patofile = new File("quality.obo")
def zfafile = new File("zfa.obo")
def gofile = new File("go-basic.obo")

def id2super = [:].withDefault { new TreeSet() }
def id2name = [:]
def id = ""
def uberon2zfa = [:]
def values = new TreeSet()
def attributes = new TreeSet()
def obsolete = new TreeSet()

patofile.eachLine { line ->
  if (line.startsWith("id:")) {
    id = line.substring(3).trim()
  }
  if (line.startsWith("name:")) {
    id2name[id] = line.substring(5).trim()
  }
  if (line.startsWith("is_a:") && line.indexOf("!")>-1) {
    def sc = line.substring(5, line.indexOf("!")-1).trim()
    id2super[id].add(sc)
  }
  if (line.indexOf("attribute_slim")>-1) {
    attributes.add(id)
  }
  if (line.indexOf("value_slim")>-1) {
    values.add(id)
  }
  if (line.indexOf("is_obsolete: true")>-1) {
    obsolete.add(id)
  }
}
ontfile.eachLine { line ->
  if (line.startsWith("id:")) {
    id = line.substring(3).trim()
  }
  if (line.startsWith("name:")) {
    id2name[id] = line.substring(5).trim()
  }
  if (line.startsWith("xref: ZFA:")) {
    uberon2zfa[id] = line.substring(5).trim()
  }
}
gofile.eachLine { line ->
  if (line.startsWith("id:")) {
    id = line.substring(3).trim()
  }
  if (line.startsWith("name:")) {
    id2name[id] = line.substring(5).trim()
  }
}
zfafile.eachLine { line ->
  if (line.startsWith("id:")) {
    id = line.substring(3).trim()
  }
  if (line.startsWith("name:")) {
    id2name[id] = line.substring(5).trim()
  }
}

String formatClassNames(String s) {
  s=s.replace("<http://purl.obolibrary.org/obo/","")
  s=s.replace(">","")
  s=s.replace("_",":")
  s
}

def cli = new CliBuilder()
cli.with {
usage: 'Self'
  h longOpt:'help', 'this information'
  o longOpt:'output-file', 'output file',args:1, required:true
  //  t longOpt:'threads', 'number of threads', args:1
  //  k longOpt:'stepsize', 'steps before splitting jobs', arg:1
}

def opt = cli.parse(args)
if( !opt ) {
  //  cli.usage()
  return
}
if( opt.h ) {
  cli.usage()
  return
}

OWLOntologyManager manager = OWLManager.createOWLOntologyManager()

OWLDataFactory fac = manager.getOWLDataFactory()
OWLDataFactory factory = fac

def ontset = new TreeSet()
OWLOntology ont = manager.loadOntologyFromOntologyDocument(zfafile)
ontset.add(ont)
ont = manager.loadOntologyFromOntologyDocument(patofile)
ontset.add(ont)

ont = manager.createOntology(IRI.create("http://lc2.eu/temp.owl"), ontset)

OWLOntology outont = manager.createOntology(IRI.create("http://phenomebrowser.net/fish-phenotype.owl"))
def onturi = "http://phenomebrowser.net/fish-phenotype.owl#"

OWLReasonerFactory reasonerFactory = null

ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor()
OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor)

OWLReasonerFactory f1 = new ElkReasonerFactory()
OWLReasoner reasoner = f1.createReasoner(ont,config)

OWLAnnotationProperty label = fac.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI())

reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)

def r = { String s ->
  if (s == "part-of") {
    factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"))
  } else if (s == "has-part") {
    factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000051"))
  } else {
    factory.getOWLObjectProperty(IRI.create("http://phenomebrowser.net/#"+s))
  }
}

def c = { String s ->
  factory.getOWLClass(IRI.create(onturi+s))
}

def id2class = [:] // maps a name to an OWLClass
ont.getClassesInSignature(true).each {
  def aa = it.toString()
  aa = formatClassNames(aa)
  if (id2class[aa] != null) {
  } else {
    id2class[aa] = it
  }
}

def addAnno = {resource, prop, cont ->
  OWLAnnotation anno = factory.getOWLAnnotation(
    factory.getOWLAnnotationProperty(prop.getIRI()),
    factory.getOWLTypedLiteral(cont))
  def axiom = factory.getOWLAnnotationAssertionAxiom(resource.getIRI(),
                                                     anno)
  manager.addAxiom(outont,axiom)
}



def phenotypes = new HashSet()
new File("phenotype.txt").splitEachLine("\t") { line ->
  def e = line[10]
  //def e2 = line[8]
  def q = line[12]
  Expando exp = new Expando()
  exp.e = e
  //  exp.e2 = e2
  exp.q = q
  phenotypes.add(exp)
}
new File("all_files").splitEachLine("\t") { line ->
  def e = line[4]
  if (e.indexOf(" ")) {
    e = e.split(" ")[0]
  }
  //  def e2 = line[8]
  def q = line[6]
  def t = line[7]
  Expando exp = new Expando()
  exp.e = e
  //  exp.e2 = e2
  exp.q = q
  phenotypes.add(exp)
  exp = new Expando()
  exp.e = e
  exp.q = t
  phenotypes.add(exp)
}

def count = 1 // global ID counter

def edone = new HashSet()
def e2p = [:]
/* Create abnormality of E classes */
phenotypes.each { exp ->
  def e = id2class[exp.e]
  if (exp.e.startsWith("UBERON")) {
    e = id2class[uberon2zfa[exp.e]]
  }
  def q = id2class[exp.q]

  //  def e2 = id2class[exp.e2]
  if (e!=null && ! (e in edone)) {
    edone.add(e)
    def cl = c("ZPO:$count")
    addAnno(cl,OWLRDFVocabulary.RDFS_LABEL,id2name[exp.e]+" phenotype")
    //    addAnno(cl,OWLRDFVocabulary.RDF_DESCRIPTION,"The mass of $oname that is used as input in a single $name is decreased.")
    manager.addAxiom(outont, factory.getOWLEquivalentClassesAxiom(
		       cl,
		       fac.getOWLObjectSomeValuesFrom(
			 r("has-part"),
			 fac.getOWLObjectIntersectionOf(
			   fac.getOWLObjectSomeValuesFrom(
			     r("part-of"), e),
			   fac.getOWLObjectSomeValuesFrom(
			     r("has-quality"), id2class["PATO:0000001"])))))
    count += 1
  }
  if (e2p[e]== null) {
    e2p[e] = new HashSet()
  }
  if (e!=null && q!=null && ! (q in e2p[e])) {
    e2p[e].add(q)
    def cl = c("ZPO:$count")
    addAnno(cl,OWLRDFVocabulary.RDFS_LABEL,id2name[exp.e]+" "+id2name[exp.q])
    manager.addAxiom(outont, factory.getOWLEquivalentClassesAxiom(
		       cl,
		       fac.getOWLObjectSomeValuesFrom(
			 r("has-part"),
			 fac.getOWLObjectIntersectionOf(
			   e,
			   fac.getOWLObjectSomeValuesFrom(
			     r("has-quality"), q)))))
    count += 1
  }
}

manager.addAxiom(outont, fac.getOWLTransitiveObjectPropertyAxiom(r("has-part")))
manager.addAxiom(outont, fac.getOWLTransitiveObjectPropertyAxiom(r("part-of")))
manager.addAxiom(outont, fac.getOWLReflexiveObjectPropertyAxiom(r("has-part")))
manager.addAxiom(outont, fac.getOWLReflexiveObjectPropertyAxiom(r("part-of")))

/*manager.addAxiom(
  outont, fac.getOWLEquivalentObjectPropertiesAxiom(
    r("part-of"), fac.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"))))
*/

OWLImportsDeclaration importDecl1 = fac.getOWLImportsDeclaration(IRI.create("file:/tmp/zfa.obo"))
manager.applyChange(new AddImport(outont, importDecl1))
importDecl1 = fac.getOWLImportsDeclaration(IRI.create("file:/tmp/pato.obo"))
manager.applyChange(new AddImport(outont, importDecl1))
//importDecl1 = fac.getOWLImportsDeclaration(IRI.create("http://purl.obolibrary.org/obo/go/go-basic.obo"))
//manager.applyChange(new AddImport(outont, importDecl1))

InferredOntologyGenerator gen = new InferredOntologyGenerator(reasoner, [new InferredSubClassAxiomGenerator(), new InferredEquivalentClassAxiomGenerator()])
//gen.fillOntology(manager, outont)

manager.saveOntology(outont, new OWLFunctionalSyntaxOntologyFormat(), IRI.create("file:"+opt.o))
System.exit(0)
