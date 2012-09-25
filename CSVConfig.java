/* 
   Ruth Helfinstein
   July 2012
   
	class csvconfig
	can either be initialized with a config file where each line in the config file 
	corresponds to a column in the csv file header and describes the mapping
	OR can be initialized with an array of attributes (from the first line of the CSV)
	and the translation can be inferred from questions to the user (like we did it before)
	
	CSVConfig stores classes and properties
	and can relate them to which column they represent in a CSV file
	
   12.09.25	  FIX class order list for more than one superclass (e.g. C subclass of B subclass of A)
	         -> have to wait and put together classOrder at the end - if add place holders for C and B
	            before we add A ends up out of order in the end.
	         Don't create the ordered list as we go along.  Instead, when we are asked
	            for an ordered list, create it then if it doesn't exist.
	            (NOTE: assumes there won't be more changes to the config - alternate
	            would be to just create it on the fly each time, which is probably OK too)

	12.09.25 Change so we are case insensitive wrt the name of a class.
	         (e.g. Shipper == shipper)
	         BUT retain the correct capitalization inside the item for display
	         lowercase the classname before put in as key (addToClassList)
	         lowercase the classnames when looking up. (getClass)
	         
	         because property names never get referred to this way, only used.
	            
	         also have to consider superclassnames because they need to have the same
	         capitalization.  Safest/cleanest way is to always look up superclass and get
	         rdf_name directly from the item (vs just use the string we have which is what we do now).
	         
	         have to change USAGE (in csv2rdf) so that instead of just getting superClassName
	         directly it calls superClassOf(class) in config and we can look it up.
	         
	         We know the right capitalization from when we're reading the line in the config
	            name,class(Staff),all
            We really only know that in addItem, so make sure to set the name correctly then.
            
	12.09.18 properties are inherited from superclasses.  Change Config getproperties(propName) to 
	         return all properties including inherited. (Didn't change HeaderClass.properties() itself
	         in case we need to sometimes just really get the indiv class properties.)  Also change getProperty 
	         to look for inherited properties!
	         
	         keep track of when a property is also a class. 
	         - csvItems becomes csvClasses and csvProperties, which map csvName to the Items
	         - add String propertyIsClass(propName) to Config to tell if something is both (returns associated class name)
	         (class can then be looked up, or string used directly by caller)
	         - update writeToFile also

	12.08.18 keep the classes in order so they are outputed superclasses before subclasses
	         keep as separate list for use when iterating (because mostly need to look up by name)
	         
            
	         
	         
	         

*/
	
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;


public class CSVConfig {
	private Map<String, HeaderClass> classes;	// list of the classes we found and the properties within them
	private ArrayList<String> classOrder;  // ordered list with superclasses before subclasses.
	
	                                                 // (one column can be both a property and a class)
	private Map<String, HeaderClass> csvClasses ;    // maps the csv name to a class
	private Map<String, HeaderProperty> csvProperties ;    // maps the csv name to a property
	
																  
	 
	public enum RDFType { CLASS, PROPERTY };
   public static final Boolean DEBUG = false;
	
	static class HeaderItem {
		String csv_name;	// the name of the column in the header of the csv file
		int csv_column;	// which column this item represents in the CSV file (-1 if none or not set yet)	
		String rdf_name;	// the name to use in the rdf fileName

	   public HeaderItem(String csv_name, String rdf_name, int csv_column) {
	   	this.csv_name = csv_name;
	   	this.csv_column = csv_column;
	   	this. rdf_name = rdf_name;
	   }
	   
	   public void set_column(int column) { csv_column = column; }
		public void set_rdf_name(String name) { rdf_name = name; }
		public void set_csv_name(String name) { csv_name = name; }

	   public int column() { return csv_column; }
		public String csv_name() { return csv_name; }
		public String rdf_name() { return rdf_name; }
		
		public String itemType() { return "Item"; }	// will be overridden by subclasses
		public String className() {return ""; }	// will be overridden

      public String toString() {
         StringBuilder s = new StringBuilder();
         
         s.append(csv_name + ",");
         s.append(itemType());
         if (!rdf_name.equals(csv_name)) s.append("(" + rdf_name + ")");
         s.append("," + className());
         return s.toString();
      }
   }

	static class HeaderProperty extends HeaderItem {
		String class_name;	// the class I am a property of
	
		public HeaderProperty(String csv_name, String rdf_name, String class_name) {
			super(csv_name, rdf_name, -1);
			this.class_name = class_name;	//doesn't check if class exists, leaves that to caller
		}
		
		public String itemType() { return "property"; }	
		public String className() {return class_name; }	
	}
	
	static class HeaderClass extends HeaderItem {
		String superClassName;
		Map<String,HeaderProperty> properties;	

		public HeaderClass(String csv_name, String rdf_name, String superClassName) {
			// won't know the properties or the csv_column when creating
			super(csv_name, rdf_name, -1);
			this.superClassName = superClassName;
			properties = new HashMap<String, HeaderProperty>();	// empty list of properties
		}
		
		public String itemType() { return "class"; }	
		public String className() {return rdf_name; }	
		public String superClassName() { return superClassName; }
		public void setSuperClassName(String name) {  superClassName = name; }

		public void addProperty(HeaderProperty property) {
			// what do we do if it exists already?
			properties.put(property.rdf_name, property);
			
		}
		
		public int numProperties() {
			// how many properties are associated with this class
			return properties.size();
		}
		
		public Set<String> properties() {
			return properties.keySet();	// list of the names of the properties
		}
				
	}
	
	
	public CSVConfig () {
		// no parameters, just initialize an empty configuration structure
		classes = new HashMap<String, HeaderClass>();   // indexed by name
		classOrder = null;       // sorted by hierarchy, create when needed
		csvClasses = new HashMap<String, HeaderClass>();   // indexed by csv_name
		csvProperties = new HashMap<String, HeaderProperty>();   // indexed by csv_name
	}
	
	public CSVConfig(String[] attributes) {
		// initialize without a config file (from just a list of the header items)
		// ask the user for help like before.
		this();	
		
		if (attributes != null) {
			int idAttribute = AskForIDAttribute(attributes);	// find out which attribute is the one to be used as ID
			String s = attributes[idAttribute].trim();
			System.out.println("(Chose " + '"' + s + '"' + ')');
			String className = AskForInstanceType();
			if (className.length() == 0) className = s;	// no different class name
			System.out.println("(Chose " + '"' + className + '"' + ')');
			String superclassName = AskForSuperClassName();
			System.out.println("(Using superclass " + '"' + superclassName + '"' + ')');
			
			// first create the superclass they just picked.
			addClass("",superclassName,"");
			
			// now create the class (id attribute)
			addClass(attributes[idAttribute], className, superclassName);
			
			// now add the rest of the lines as properties of that class.
			for (int i = 0; i < attributes.length; i++) {
				if (i != idAttribute) {
					addProperty(attributes[i], attributes[i], className);
				}
			}
			
		}
	}
	
   

	private void addInClassOrder(String key_name) {
	         // now figure out its order for classOrder
	      
	   if (classOrder == null)
	      classOrder = new ArrayList<String>();
	      
      int target = -1;   // location to put this class

      for (int i = 0; i < classOrder.size(); i++) {   // go through classes and put this one in the right place
         String thisEntry = classOrder.get(i);
         if (isAncestorOf(thisEntry, key_name))  {  
            // everything before this is "less than" or "equal to" us.  
            if (DEBUG) System.out.println(key_name + " is an ancestor of " + thisEntry + " : " + i);
            target = i;
            break;   // found where to put it
         }
         
      }
      if (target == -1) {
         target = classOrder.size();   // never found a subclass, just put us at the end.
         if (DEBUG) System.out.println(key_name + " goes at end : " + target);
      }
    
      classOrder.add(target, key_name);   

	}


	public String[] classes() { 
	   // generate a list sorted by class hierarchy
	   
	   
	   if (classOrder == null) {
	      // generate an ordered list from the set of classes
	      classOrder = new ArrayList<String>();
	      
	      for (String s : classes.keySet()) {
            if (DEBUG) System.out.println("(" + s + ")" );

	         addInClassOrder(s);
	      }
	   }
	   
		return  classOrder.toArray(new String[classOrder.size()]); 	// the names of the classes in hierarchy order */
		
	}
	
	
	
	public ArrayList<String> getProperties(String className) {
	   // return ALL the properties associated with the class
	   // this includes properties inherited from its superclass(es)
	   
	   ArrayList<String> l = new ArrayList<String>();  // build a list to return
		HeaderClass c = getClass(className);
		while (c != null) {
	      for (String s : c.properties()) {
	         l.add(s);
	      }
         c = getClass(c.superClassName());   
		} 
		
		return l;   // if no properties, this will be empty.
	}
	
	public int numClasses() {
		return classes.size();
	}
	
	public String superClassOf(HeaderClass c) {
		if (c != null) {
		   HeaderClass s = getClass(c.superClassName());
		   if (s != null) return s.rdf_name();    // make sure we get the accurate name
		   else return "";   // we may not have a superclass.
		} 
		
		return null;
		
	}
	
	public String superClassOf(String className) {
		HeaderClass c = getClass(className);
	   return superClassOf(c);
	}
	
	private Set<String> ancestorsOf(String className) {
	   HashSet<String> a = new HashSet<String>();
	   
	   HeaderClass c = getClass(className);
	   if (c != null) {
	      c = getClass(c.superClassName()); // first ancestor, if any
	      while (c != null) {
	         a.add(c.rdf_name()); // add to the set
	         c = getClass(c.superClassName());   // up the chain
	      }
	      
	   }
	   
	   return a;
	
	}
	
	public Boolean isAncestorOf(String className, String ancestorName) {
	   // check if the class named ancestorName is anywhere in the superclass list of className
	   
	   Set<String> a = ancestorsOf(className);
	   if (DEBUG) {
	     // System.out.println("Ancestors of " + className + ": " + a);
	   }
	   return a.contains(ancestorName);

	
	}
	
	public String propertyIsClass(HeaderItem p) {
	   // given the rdf_name of a property, is that property also a class?
	   // have to send in the header item itself not just the string because
	   // can't currently look up a property directly by rdf_name without knowing its class.

	   // return the rdf_name of the matching class, if any, or an empty string.
	   // NOTE: the rdf_names could be different, but the csv_names will always match 
	   // if it's the same item.
	   
	   String s = "";
	   if (p != null) {
         HeaderItem item = csvClasses.get(p.csv_name());
         if (item != null) s = item.rdf_name();
	   }
	   
	   return s;
	   
	   
	}
	
	private void addCSVClass(HeaderClass item) {
		// used internally when we already have an existing item to add.
		// adds the item to the list, indexed by csv_name 
		// if csv_name is empty, just don't add it to the list - it's a placeholder item
      
 		if (item.csv_name().length() > 0) {
 		   // add it to the list // check if it's there first?
 		   csvClasses.put (item.csv_name(), item);  
 		}
	}

	private void addCSVProperty(HeaderProperty item) {
		// used internally when we already have an existing item to add.
		// adds the item to the list, indexed by csv_name 
		// if csv_name is empty, just don't add it to the list - it's a placeholder item
      
 		if (item.csv_name.length() > 0) {
 		   // add it to the list // check if it's there first?
 		   csvProperties.put(item.csv_name(), item);
 		}
	}
		
	
	public HeaderClass addClass(String csv_name, String rdf_name, String superclass_name) {
		// checks if a class indexed by rdf_name exists and adds it if it doesn't
		// also updates fields which have changed and adds superclass if it doesn't exist
		
		// also adds it to the list of items using addItem;
		
		// NOTE: does NOT look up by csv_name.  The only scenario we have where a class will be added
		// as a placeholder comes from the translation document, where a class is referenced before 
		// it is defined.  This should ALWAYS be the rdf_name for the class.
		// I thought about putting in a case where we know the csv_name first, but decided
		// we might have a legit case where the csv_name for one thing is the rdf_name for another
		// so this wouldn't work.

		if (DEBUG) System.out.println("	>>Adding class:     " + csv_name + "(" + rdf_name + ")" + " subclass of " + superclass_name);
		String key_name = new String(rdf_name);
		key_name = key_name.toLowerCase();  // key name always lower case for lookups
		                                             // RH 2012.09.24
		HeaderClass c = getClass(key_name);	

		if (c == null) {
		   // create a new item for this class
		   // create with correct case version of name (rdf_name) not lowercase
			c = new HeaderClass(csv_name, rdf_name, superclass_name);
			addToClassList(key_name, c);
			
		} else {
			// if it already exists, we might be updating after we put in placeholders before

			if ((superclass_name.length() > 0) && !superclass_name.equals(c.superClassName())) {
				c.setSuperClassName(superclass_name);
		   }
			if ( (csv_name.length() > 0) && !csv_name.equals(c.csv_name())) {
				c.set_csv_name(csv_name);
			}
		}
		
		// add it to the list of items if it's not already there
		addCSVClass(c );  
		
		// in all cases, we want to create the superclass if it doesn't exist.
		if ((superclass_name.length() > 0) && (!classExists(superclass_name))) {
			addClass("", superclass_name, "");		// put in a placeholder
		}
		
		return c;
	}
	
	public HeaderClass getClass(String name) {
			// classes are stored as lowercase, so make sure we're checking for lowercase name
			// but don't change actual string (I was surprised but it toLowerCase seems to work in place.)
			if (name.length() > 0) {
            String key_name = new String(name); 
            
            return classes.get(key_name.toLowerCase());	
         } else return null;
	}
	
	
	public HeaderProperty getProperty(String className, String propName) {
		HeaderClass c = getClass(className);
		HeaderProperty prop = null;
		while (c != null && prop == null) { //there is a class and we haven't found the property
		   prop = c.properties.get(propName);
		   c = getClass(c.superClassName());
		}
		return prop;	// will be null if the class or the property don't exist.
	}
	

   private void addToClassList(String key_name, HeaderClass newClass) {
      // put this item onto our list of classes
      // - add to hashmap
      // We have already determined that this needs to be added to the list.
      
      // key name is all lower case before it is sent in.
      
      if (key_name.length() > 0) {
         classes.put(key_name, newClass);
         
      } else {
      // don't add an empty key!! 
         if (DEBUG) System.out.println("       OOPS! key name is empty");
      }
      
   }

	
	
	public void addProperty(String csv_name, String rdf_name, String class_name) {
		 if (DEBUG) System.out.println("	Adding property:  " + csv_name + " (" + rdf_name + ")");
		 HeaderProperty p = new HeaderProperty(csv_name, rdf_name, class_name);
		 HeaderClass c = getClass(class_name);
		if (c == null)  {
			c = addClass("", class_name, "");	// add placeholder
		}
		c.addProperty(p);
		addCSVProperty(p);	   
		
	}
	
	public void addItem(String itemType, String csv_name, String rdf_name, String class_name) {
		// add a Header item of the right type
		// for now, if the string is exactly "class" make a class
		// if it starts with "prop" make a property
		// otherwise just ignore it 
		// if (DEBUG) System.out.println("Adding item: " + itemType + " " + csv_name + " " + rdf_name + " " + class_name);

		if (itemType.equals("class")) {
		   HeaderClass c = addClass(csv_name, rdf_name, class_name);
		   // at this point we KNOW the class has been created AND we know that
		   // rdf_name is the capitalization that we want to use so set it.  RH 12.09.24
			if (!rdf_name.equals(c.rdf_name())) {
			   c.set_rdf_name(rdf_name);
			   if (DEBUG) System.out.println("RDF Name is " + c.rdf_name());
			}

		   
		} else if (itemType.startsWith("prop")) {
		   addProperty(csv_name, rdf_name, class_name);
		   
		} else 	{
		// otherwise it says ignore so ignore it.
			System.out.println("Item " + csv_name + " is not defined as a class or a property (ignoring)");
		}

	}
	
	public boolean classExists(String name) {
		// simple case assume name is the rdf_name.  Could also check if it's the csv name for any CLASS
		return classes.containsKey(name);
	}
	
	public void setItemColumn(String csv_name, int column) {
		// look up an item by it's name in the csv file and set it's column number
		// There may be more than one which matches this name (e.g. a property and a class)
 

      HeaderItem item = csvClasses.get(csv_name);
      if (item != null) item.set_column(column);   
      item = csvProperties.get(csv_name);
      if (item != null) item.set_column(column);   


		// if it's not there that means the column doesn't exist in the config file.
		// for now that means we are going to ignore it.
		
	}

	
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
		  		num = Console.readInt("Which attribute will be used as the class?");
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
		// if it's blank, return a blank.
		String instanceType = "";
		
		String response;
		  
		response = Console.readLine("Please enter a string to use for the name of the class:").trim();
		if (response.length() > 0) {
			// trim off quotes if they put them there.
			instanceType = response.replaceAll("^\"|\"$", "").trim().replaceAll(" ","_");
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

   
	public void writeToFile(String fileName) {
		// write out the configuration to a file 
		// do this based on the csvitems lists
		Out out;
		if (fileName.length() == 0 ) out = new Out();
		else out = new Out(fileName);
		
		for (HeaderItem item : csvClasses.values()) {
		   // go through each item
		      out.println(item.toString());
		}
		
		for (HeaderItem item : csvProperties.values()) {
		   // go through each item
		      out.println(item.toString());
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
		   reader.close();	// done with the file for our test
		   
		   CSVConfig config = new CSVConfig(attributes);	// create a config file from the line with asking user questions.
		   
		   
		   // write out what we did.
		   System.out.println("Classes");
		   for (String cName : config.classes()) {
		   	System.out.print(cName + ": ");
		   	HeaderClass c = config.getClass(cName);
		   	for (String pName : c.properties()) {
		   		System.out.print(pName + " ");
		   	}
		   	System.out.println("");
		   }
      }
	  catch (IOException ioe) {
			System.err.println("Could not open file:" + fileName);
	  }
	}
	

}