package gr.uom.java.ast;

import gr.uom.java.ast.decomposition.cfg.AbstractVariable;
import gr.uom.java.ast.decomposition.cfg.CompositeVariable;
import gr.uom.java.ast.decomposition.cfg.PlainVariable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

public class LibraryClassStorage {
	private static LibraryClassStorage instance;
	private Map<IClassFile, CompilationUnit> compilationUnitMap;
	private Set<IClassFile> unMatchedClassFiles;
	private Map<MethodDeclaration, LinkedHashSet<MethodDeclaration>> methodInvocationMap;
	private Map<MethodDeclaration, LinkedHashSet<VariableDeclaration>> definedFieldMap;
	private Map<MethodDeclaration, LinkedHashSet<VariableDeclaration>> usedFieldMap;
	private Map<IType, LinkedHashSet<IType>> subTypeMap;
	private Map<MethodDeclaration, LinkedHashSet<MethodDeclaration>> overridingMethodMap;
	private Map<MethodDeclaration, HashMap<VariableDeclaration, LinkedHashSet<MethodDeclaration>>> methodInvocationThroughReferenceMap;
	
	private LibraryClassStorage() {
		this.compilationUnitMap = new HashMap<IClassFile, CompilationUnit>();
		this.unMatchedClassFiles = new LinkedHashSet<IClassFile>();
		this.methodInvocationMap = new HashMap<MethodDeclaration, LinkedHashSet<MethodDeclaration>>();
		this.definedFieldMap = new HashMap<MethodDeclaration, LinkedHashSet<VariableDeclaration>>();
		this.usedFieldMap = new HashMap<MethodDeclaration, LinkedHashSet<VariableDeclaration>>();
		this.subTypeMap = new HashMap<IType, LinkedHashSet<IType>>();
		this.overridingMethodMap = new HashMap<MethodDeclaration, LinkedHashSet<MethodDeclaration>>();
		this.methodInvocationThroughReferenceMap = new HashMap<MethodDeclaration, HashMap<VariableDeclaration, LinkedHashSet<MethodDeclaration>>>();
	}
	
	public static LibraryClassStorage getInstance() {
		if(instance == null) {
			instance = new LibraryClassStorage();
		}
		return instance;
	}
	
	public CompilationUnit getCompilationUnit(IClassFile classFile) {
		if(compilationUnitMap.containsKey(classFile)) {
			CompilationUnit compilationUnit = compilationUnitMap.get(classFile);
			return compilationUnit;
		}
		else {
			CompilationUnit compilationUnit = null;
			try {
				if(!unMatchedClassFiles.contains(classFile)) {
					ASTParser parser = ASTParser.newParser(AST.JLS3);
					parser.setSource(classFile);
					parser.setResolveBindings(true);
					compilationUnit = (CompilationUnit)parser.createAST(null);
					compilationUnitMap.put(classFile, compilationUnit);
				}
			}
			catch(IllegalStateException e) {
				unMatchedClassFiles.add(classFile);
			}
			return compilationUnit;
		}
	}
	
	public Set<IType> getSubTypes(IType superType) {
		if(subTypeMap.containsKey(superType)) {
			Set<IType> subTypes = subTypeMap.get(superType);
			LinkedHashSet<IType> subTypesOfAbstractSubTypes = new LinkedHashSet<IType>();
			try {
				for(IType subType: subTypes) {
					if(Flags.isAbstract(subType.getFlags()) && !subType.equals(superType)) {
						subTypesOfAbstractSubTypes.addAll(getSubTypes(subType));
					}
				}
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
			LinkedHashSet<IType> finalSubTypes = new LinkedHashSet<IType>();
			finalSubTypes.addAll(subTypes);
			finalSubTypes.addAll(subTypesOfAbstractSubTypes);
			return finalSubTypes;
		}
		else {
			IPackageFragment packageFragment = superType.getPackageFragment();
			final LinkedHashSet<IType> subTypes = new LinkedHashSet<IType>();
			final LinkedHashSet<IType> subTypesOfAbstractSubTypes = new LinkedHashSet<IType>();
			try {
				SearchPattern searchPattern = SearchPattern.createPattern(superType, IJavaSearchConstants.IMPLEMENTORS);
				SearchEngine searchEngine = new SearchEngine();
				IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] {packageFragment}, false);
				SearchRequestor requestor = new TypeSearchRequestor(subTypes);
				searchEngine.search(searchPattern, new SearchParticipant[] {SearchEngine.getDefaultSearchParticipant()},
						scope, requestor, null);
				subTypeMap.put(superType, subTypes);
				for(IType subType: subTypes) {
					if(Flags.isAbstract(subType.getFlags()) && !subType.equals(superType)) {
						subTypesOfAbstractSubTypes.addAll(getSubTypes(subType));
					}
				}
			} catch (JavaModelException e) {
				e.printStackTrace();
			} catch (CoreException e) {
				e.printStackTrace();
			}
			LinkedHashSet<IType> finalSubTypes = new LinkedHashSet<IType>();
			finalSubTypes.addAll(subTypes);
			finalSubTypes.addAll(subTypesOfAbstractSubTypes);
			return finalSubTypes;
		}
	}
	
	public void addInvokedMethod(MethodDeclaration originalMethod, MethodDeclaration invokedMethod) {
		if(methodInvocationMap.containsKey(originalMethod)) {
			LinkedHashSet<MethodDeclaration> invokedMethods = methodInvocationMap.get(originalMethod);
			invokedMethods.add(invokedMethod);
		}
		else {
			LinkedHashSet<MethodDeclaration> invokedMethods = new LinkedHashSet<MethodDeclaration>();
			invokedMethods.add(invokedMethod);
			methodInvocationMap.put(originalMethod, invokedMethods);
		}
	}
	
	public void addInvokedMethodThroughReference(MethodDeclaration originalMethod, MethodDeclaration invokedMethod, VariableDeclaration fieldReference) {
		if(methodInvocationThroughReferenceMap.containsKey(originalMethod)) {
			HashMap<VariableDeclaration, LinkedHashSet<MethodDeclaration>> invokedMethodsThroughReference = methodInvocationThroughReferenceMap.get(originalMethod);
			if(invokedMethodsThroughReference.containsKey(fieldReference)) {
				LinkedHashSet<MethodDeclaration> invokedMethods = invokedMethodsThroughReference.get(fieldReference);
				invokedMethods.add(invokedMethod);
			}
			else {
				LinkedHashSet<MethodDeclaration> invokedMethods = new LinkedHashSet<MethodDeclaration>();
				invokedMethods.add(invokedMethod);
				invokedMethodsThroughReference.put(fieldReference, invokedMethods);
			}
		}
		else {
			LinkedHashSet<MethodDeclaration> invokedMethods = new LinkedHashSet<MethodDeclaration>();
			invokedMethods.add(invokedMethod);
			HashMap<VariableDeclaration, LinkedHashSet<MethodDeclaration>> invokedMethodsThroughReference = new HashMap<VariableDeclaration, LinkedHashSet<MethodDeclaration>>();
			invokedMethodsThroughReference.put(fieldReference, invokedMethods);
			methodInvocationThroughReferenceMap.put(originalMethod, invokedMethodsThroughReference);
		}
	}
	
	public Set<VariableDeclaration> getInvocationReferences(MethodDeclaration originalMethod) {
		if(methodInvocationThroughReferenceMap.containsKey(originalMethod)) {
			HashMap<VariableDeclaration, LinkedHashSet<MethodDeclaration>> invokedMethodsThroughReference = methodInvocationThroughReferenceMap.get(originalMethod);
			return invokedMethodsThroughReference.keySet();
		}
		return null;
	}
	
	public void addOverridingMethod(MethodDeclaration abstractMethod, MethodDeclaration overridingMethod) {
		if(overridingMethodMap.containsKey(abstractMethod)) {
			LinkedHashSet<MethodDeclaration> overridingMethods = overridingMethodMap.get(abstractMethod);
			overridingMethods.add(overridingMethod);
		}
		else {
			LinkedHashSet<MethodDeclaration> overridingMethods = new LinkedHashSet<MethodDeclaration>();
			overridingMethods.add(overridingMethod);
			overridingMethodMap.put(abstractMethod, overridingMethods);
		}
	}
	
	public void setDefinedFields(MethodDeclaration method, LinkedHashSet<VariableDeclaration> fields) {
		definedFieldMap.put(method, fields);
	}
	
	public void setUsedFields(MethodDeclaration method, LinkedHashSet<VariableDeclaration> fields) {
		usedFieldMap.put(method, fields);
	}
	
	public boolean isAnalyzed(MethodDeclaration method) {
		if(definedFieldMap.containsKey(method) && usedFieldMap.containsKey(method))
			return true;
		else
			return false;
	}
	
	public LinkedHashSet<VariableDeclaration> getRecursivelyDefinedFields(MethodDeclaration method,
			Set<MethodDeclaration> processedMethods) {
		LinkedHashSet<VariableDeclaration> definedFields = new LinkedHashSet<VariableDeclaration>();
		definedFields.addAll(definedFieldMap.get(method));
		processedMethods.add(method);
		LinkedHashSet<MethodDeclaration> invokedMethods = methodInvocationMap.get(method);
		if(invokedMethods != null) {
			for(MethodDeclaration invokedMethod : invokedMethods) {
				if(!processedMethods.contains(invokedMethod)) {
					if(invokedMethod.getBody() != null) {
						if((invokedMethod.getModifiers() & Modifier.NATIVE) != 0) {
							//method is native
						}
						else {
							definedFields.addAll(getRecursivelyDefinedFields(invokedMethod, processedMethods));
						}
					}
					else {
						LinkedHashSet<MethodDeclaration> overridingMethods = overridingMethodMap.get(invokedMethod);
						processedMethods.add(invokedMethod);
						if(overridingMethods != null) {
							for(MethodDeclaration overridingMethod : overridingMethods) {
								if((overridingMethod.getModifiers() & Modifier.NATIVE) != 0) {
									//method is native
								}
								else {
									definedFields.addAll(getRecursivelyDefinedFields(overridingMethod, processedMethods));
								}
							}
						}
					}
				}
			}
		}
		return definedFields;
	}
	
	public LinkedHashSet<VariableDeclaration> getRecursivelyUsedFields(MethodDeclaration method,
			Set<MethodDeclaration> processedMethods) {
		LinkedHashSet<VariableDeclaration> usedFields = new LinkedHashSet<VariableDeclaration>();
		usedFields.addAll(usedFieldMap.get(method));
		processedMethods.add(method);
		LinkedHashSet<MethodDeclaration> invokedMethods = methodInvocationMap.get(method);
		if(invokedMethods != null) {
			for(MethodDeclaration invokedMethod : invokedMethods) {
				if(!processedMethods.contains(invokedMethod)) {
					if(invokedMethod.getBody() != null) {
						if((invokedMethod.getModifiers() & Modifier.NATIVE) != 0) {
							//method is native
						}
						else {
							usedFields.addAll(getRecursivelyUsedFields(invokedMethod, processedMethods));
						}
					}
					else {
						LinkedHashSet<MethodDeclaration> overridingMethods = overridingMethodMap.get(invokedMethod);
						processedMethods.add(invokedMethod);
						if(overridingMethods != null) {
							for(MethodDeclaration overridingMethod : overridingMethods) {
								if((overridingMethod.getModifiers() & Modifier.NATIVE) != 0) {
									//method is native
								}
								else {
									usedFields.addAll(getRecursivelyUsedFields(overridingMethod, processedMethods));
								}
							}
						}
					}
				}
			}
		}
		return usedFields;
	}
	
	public LinkedHashSet<AbstractVariable> getRecursivelyDefinedFieldsThroughReference(MethodDeclaration method,
			AbstractVariable fieldReference, Set<MethodDeclaration> processedMethods) {
		LinkedHashSet<AbstractVariable> definedFields = new LinkedHashSet<AbstractVariable>();
		processedMethods.add(method);
		HashMap<VariableDeclaration, LinkedHashSet<MethodDeclaration>> invokedMethodsThroughReference = methodInvocationThroughReferenceMap.get(method);
		if(invokedMethodsThroughReference != null) {
			VariableDeclaration reference = null;
			if(fieldReference instanceof PlainVariable) {
				PlainVariable plain = (PlainVariable)fieldReference;
				reference = plain.getName();
			}
			else if(fieldReference instanceof CompositeVariable) {
				CompositeVariable composite = (CompositeVariable)fieldReference;
				reference = composite.getFinalVariable().getName();
			}
			LinkedHashSet<MethodDeclaration> invokedMethods = invokedMethodsThroughReference.get(reference);
			if(invokedMethods != null) {
				for(MethodDeclaration invokedMethod : invokedMethods) {
					if(!processedMethods.contains(invokedMethod)) {
						if((invokedMethod.getModifiers() & Modifier.NATIVE) != 0) {
							//method is native
						}
						else {
							LinkedHashSet<VariableDeclaration> definedFieldsInInvokedMethod = definedFieldMap.get(invokedMethod);
							if(definedFieldsInInvokedMethod != null) {
								for(VariableDeclaration variableDeclaration : definedFieldsInInvokedMethod) {
									PlainVariable rightSide = new PlainVariable(variableDeclaration);
									AbstractVariable definedField = composeVariable(fieldReference, rightSide);
									definedFields.add(definedField);
								}
							}
							LinkedHashSet<VariableDeclaration> usedFieldsInInvokedMethod = usedFieldMap.get(invokedMethod);
							if(usedFieldsInInvokedMethod != null) {
								for(VariableDeclaration variableDeclaration : usedFieldsInInvokedMethod) {
									PlainVariable rightSide = new PlainVariable(variableDeclaration);
									AbstractVariable usedField = composeVariable(fieldReference, rightSide);
									definedFields.addAll(getRecursivelyDefinedFieldsThroughReference(invokedMethod, usedField, processedMethods));
								}
							}
						}
					}
				}
			}
		}
		return definedFields;
	}
	
	public LinkedHashSet<AbstractVariable> getRecursivelyUsedFieldsThroughReference(MethodDeclaration method,
			AbstractVariable fieldReference, Set<MethodDeclaration> processedMethods) {
		LinkedHashSet<AbstractVariable> usedFields = new LinkedHashSet<AbstractVariable>();
		processedMethods.add(method);
		HashMap<VariableDeclaration, LinkedHashSet<MethodDeclaration>> invokedMethodsThroughReference = methodInvocationThroughReferenceMap.get(method);
		if(invokedMethodsThroughReference != null) {
			VariableDeclaration reference = null;
			if(fieldReference instanceof PlainVariable) {
				PlainVariable plain = (PlainVariable)fieldReference;
				reference = plain.getName();
			}
			else if(fieldReference instanceof CompositeVariable) {
				CompositeVariable composite = (CompositeVariable)fieldReference;
				reference = composite.getFinalVariable().getName();
			}
			LinkedHashSet<MethodDeclaration> invokedMethods = invokedMethodsThroughReference.get(reference);
			if(invokedMethods != null) {
				for(MethodDeclaration invokedMethod : invokedMethods) {
					if(!processedMethods.contains(invokedMethod)) {
						if((invokedMethod.getModifiers() & Modifier.NATIVE) != 0) {
							//method is native
						}
						else {
							LinkedHashSet<VariableDeclaration> usedFieldsInInvokedMethod = usedFieldMap.get(invokedMethod);
							if(usedFieldsInInvokedMethod != null) {
								for(VariableDeclaration variableDeclaration : usedFieldsInInvokedMethod) {
									PlainVariable rightSide = new PlainVariable(variableDeclaration);
									AbstractVariable usedField = composeVariable(fieldReference, rightSide);
									usedFields.add(usedField);
									usedFields.addAll(getRecursivelyUsedFieldsThroughReference(invokedMethod, usedField, processedMethods));
								}
							}
						}
					}
				}
			}
		}
		return usedFields;
	}
	
	private AbstractVariable composeVariable(AbstractVariable leftSide, AbstractVariable rightSide) {
		if(leftSide instanceof CompositeVariable) {
			CompositeVariable leftSideCompositeVariable = (CompositeVariable)leftSide;
			PlainVariable finalVariable = leftSideCompositeVariable.getFinalVariable();
			CompositeVariable newRightSide = new CompositeVariable(finalVariable.getName(), rightSide);
			AbstractVariable newLeftSide = leftSideCompositeVariable.getLeftPart();
			return composeVariable(newLeftSide, newRightSide);
		}
		else {
			return new CompositeVariable(leftSide.getName(), rightSide);
		}
	}
}
