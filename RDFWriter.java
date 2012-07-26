public class RDFWriter {
// for now this just writes very simple RDF to the file specified, or to the stdout
// but I'm doing it as a class to abstract it away from reading from the csv


	// constructor
	public RDFWriter() {
		// no params = write to stdout
		out = new Out();
		
	
	}
	
	public RDFWriter(String s) {
	// file name
		out = new Out(s);
	}
	

	public void endRDF() {
		// write whatever goes at the botom of the rdf file
		// and close the file.
		out.println("</rdf:RDF>");
		out.close();
	}
	
	public void writePropertyTag(String attributeName, String className) {
		out.printf( "<rdf:Property rdf:ID=\"%s\">\n", attributeName);
		out.printf( "  <rdfs:domain rdf:resource=\"#%s\"/>\n", className);  
		out.println("  <rdfs:range rdf:resource=\"http://www.w3.org/2001/XMLSchema#string\"/>"); 
		out.println("</rdf:Property>");
		out.println(" ");
	}
	
	public void writeClassInfo(String className, String superclass) {
		out.printf("<rdf:Description rdf:ID=\"%s\">\n", className);   
		out.println("  <rdf:type rdf:resource=\"http://www.w3.org/2000/01/rdf-schema#Class\"/>");
		out.printf("  <rdfs:subClassOf rdf:resource=\"#%s\"/>", superclass);
		out.println("\n</rdf:Description>");
		out.println(" ");
	}

	public void startInstance(String id, String instanceType) {
		// start a new instance, writes the opening rdf:description tag, and the rdf:type tag
		out.printf("<rdf:Description rdf:ID=\"%s\">\n", id);
		out.printf("  <rdf:type rdf:resource=\"%s\"/>\n", instanceType);
	}

	public void endInstance() {
		out.printf("</rdf:Description>\n");
		out.println(" ");
}
	
	
	public void writeAttributeData(String attName, String attData) {
		out.printf("  <sample:%s>%s</sample:%s>\n", attName, attData, attName);
	}
	
	public void startRDF(String[] attributes, int idAttribute, String className, String superclassName) {
		// write whatever goes at the top of the rdf file
		
		// replace any spaces in the attributes with underscores
		// replace any blank attributes with "unlabeled1", "unlabeled2" -- RH 12.07.03
		int numBlanks = 0;
		for (int i = 0; i < attributes.length; i++) {
			attributes[i] = attributes[i].trim();
			if (attributes[i].equals("")) {
				numBlanks++;
				attributes[i] = "unlabeled" + numBlanks;
			} else {
				attributes[i] = attributes[i].replaceAll(" ","_");
			}
		}

		// open the rdf tag, and write header info
		out.println("<rdf:RDF");
  		out.println("  xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"");
  		out.println("  xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"");
  		out.println("  xmlns:owl=\"http://www.w3.org/2002/07/owl#\"");
  		out.println("  xmlns:sample=\"http://www.test.nql.org/schemas/sample#\">");
  		out.println("<owl:Ontology rdf:about=\"http://www.w3.org/2000/01/rdf-schema#\"/>");
		out.println(" ");
	
		// write a description tag for the main class)	
		// if superclass isn't "Thing", need to put in an extra definition
		if (!superclassName.equals("Thing")) writeClassInfo(superclassName, "Thing");		
		writeClassInfo(className, superclassName);
		
		// write a property tag for the rest of the attributes
		for (int i = 0; i < attributes.length; i++) {
			if (i != idAttribute) {
				writePropertyTag(attributes[i], className);	//writes a property tag for each  instance
			}
		}

	}

	// instance variables
	private  Out out;

}