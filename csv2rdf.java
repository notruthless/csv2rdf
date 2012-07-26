/* Translate CSV to RDF in a very simple translation.
	Assumes the first line is the attributes
	Take as input which attribute is the ID of the instance 
	Take as input the type of the instance (which is not in the CSV)
	
	The CSV file must be complete (same number of items on each line)
	Does very very minimal error checking 
	
	compile:
	javac -classpath '.:opencsv-2.3.jar' csv2rdf.java
	
	make jar:
	jar cmf manifest.txt csv2rdf.jar *.class au

	run
	java -jar csv2rdf.jar <csv file name>
	
	
	changes:
	12.06.27 change spaces to underscores in attribute names.
				trim leading/trailing spaces from data.
	
*/
	
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;


public class csv2rdf {

	public static int AskForIDAttribute(String[] attributes) {
		// given an array of strings, ask the user which one to use as the id field.
		// but for now, just automatically return the index of the attribute "name"
		// I know that will be the second element for our test file so to start just return 1

		for (int i = 0; i < attributes.length; i++) {
				System.out.printf("%d: %s\n", i, attributes[i]);
		
		}

		int num = -1;
		while(true)
		  {  
			// loop until they enter a valid number
		  		num = Console.readInt("Which attribute will be used as the name of the instance?");
				if (num >= 0 && num < attributes.length) {
					break;
				} else {
					Console.printPrompt("Not a valid entry, please enter a number between 0 and " + (attributes.length - 1) + "\n");
				}	 
		  }


		return num;
	
	}

	public static String AskForInstanceType() {
		// ask the user for a string to use as the type of the instance.
		// for now I happen to know it will be "staff"
		String instanceType;
		
		String response;
		  
		response = Console.readLine("Please enter a string to use for the type of the instance:");
		if (response.trim() != "") {
			// trim off quotes if they put them there.
			instanceType = response.replaceAll("^\"|\"$", "").trim().replaceAll(" ","_");
		} else {
			instanceType = "Staff";
		}
		
		return instanceType;
	}

	public static String AskForSuperClassName() {
		// ask the user if they want an "all" class (instead of just Thing.
		// an answer starting with y gets an all class. Any other answer doesn't

		String superclass;
		
		String response;
		  
		response = Console.readLine("Do you want an \"all\" class (y or n)?");
		response = response.trim();
		char firstChar = (response.length() == 0) ? 'n' : response.charAt(0);
		if (firstChar == 'y' || firstChar == 'Y') {
			superclass = "all";
		} else {
			superclass = "Thing";
		}
		
		return superclass;
	}

	
	public static String BaseFileName(String name) {
		// takes a file name (like input.csv) and returns it without the extension
		int p = name.lastIndexOf(".");
		
		if (p == -1) {
			return name;
		} else {
			return name.substring(0,p);
		}
	}

	private static final String INPUT_FILE="input.csv";
   
	
	public static void main(String[] args) throws IOException {
		
		
		String fileName, outputFile;
		
		if (args.length > 0) {
			fileName = args[0];
		} else {
			fileName = INPUT_FILE;	// default if none is specified.
		}
		
		
	  	try {  // handle error where file doesn't exist.
			CSVReader reader;
			reader = new CSVReader(new FileReader(fileName));
			System.out.println("Reading CSV from " + fileName);
			String [] attributes = reader.readNext(); // header line
			
			if (attributes != null) {
				int numAttributes = attributes.length;
				
				int idAttribute = AskForIDAttribute(attributes);	// find out which attribute is the one to be used as ID
				System.out.println("(Chose " + '"' + attributes[idAttribute].trim() + '"' + ')');
				String instanceType = AskForInstanceType();
				System.out.println("(Chose " + '"' + instanceType + '"' + ')');
				String superclassName = AskForSuperClassName();
				System.out.println("(Using superclass " + '"' + superclassName + '"' + ')');
				
				outputFile = BaseFileName(fileName) + ".rdf";
				System.out.println("Writing RDF to " + outputFile);
				RDFWriter writer = new RDFWriter(outputFile);		
				writer.startRDF(attributes, idAttribute, instanceType, superclassName);	// this writes header and beginning part of the file.
																		// also fixes up attributes.
				
				String [] nextLine;
				while ((nextLine = reader.readNext()) != null) {
					// process one line at a time.
					// assuming well formed input - length of each line is exactly the same as length of header.
					// add some error checking
					
					if (nextLine.length == numAttributes) {
						nextLine[idAttribute] = nextLine[idAttribute].trim();
						writer.startInstance(nextLine[idAttribute], instanceType);
						for (int i = 0; i < nextLine.length; i++) {
							if (i != idAttribute) {
								nextLine[i] = nextLine[i].trim();
								if (!nextLine[i].equals("")) {
									writer.writeAttributeData(attributes[i], nextLine[i]);
								} // don't write attributes without Data. -- RH 12.07.02
							}
						}
						writer.endInstance();
		
					} else {
						System.out.println("Badly formed instance, skipping");
						System.out.print("   <");
						for (int i=0;i<nextLine.length; i++) {
							System.out.print(nextLine[i] + " ");
						}
						System.out.println(">");
					}
				}
				writer.endRDF();	// anything that goes at the end of the RDF (and closes the file)
	
			} else {
				System.out.println("Empty input file:" + fileName);
			}
      }
	  catch (IOException ioe) {
			System.err.println("Could not open file:" + fileName);
	  }

		
		

	}
	

}