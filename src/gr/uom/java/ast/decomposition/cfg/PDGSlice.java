package gr.uom.java.ast.decomposition.cfg;

import gr.uom.java.ast.LocalVariableDeclarationObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.decomposition.AbstractStatement;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.VariableDeclaration;

public class PDGSlice extends Graph {
	private MethodObject method;
	private BasicBlock boundaryBlock;
	private PDGNode nodeCriterion;
	private VariableDeclaration localVariableCriterion;
	private Set<PDGNode> sliceNodes;
	private Set<PDGNode> remainingNodes;
	private Set<VariableDeclaration> passedParameters;
	private Set<PDGNode> indispensableNodes;
	private Set<PDGNode> removableNodes;
	private Set<VariableDeclaration> returnedVariablesInOriginalMethod;
	
	public PDGSlice(PDG pdg, BasicBlock boundaryBlock, PDGNode nodeCriterion,
			VariableDeclaration localVariableCriterion) {
		super();
		this.method = pdg.getMethod();
		this.returnedVariablesInOriginalMethod = pdg.getReturnedVariables();
		this.boundaryBlock = boundaryBlock;
		this.nodeCriterion = nodeCriterion;
		this.localVariableCriterion = localVariableCriterion;
		Set<PDGNode> regionNodes = pdg.blockBasedRegion(boundaryBlock);
		for(PDGNode node : regionNodes) {
			nodes.add(node);
		}
		for(GraphEdge edge : pdg.edges) {
			PDGDependence dependence = (PDGDependence)edge;
			if(nodes.contains(dependence.src) && nodes.contains(dependence.dst))
				edges.add(dependence);
		}
		this.sliceNodes = new TreeSet<PDGNode>();
		sliceNodes.addAll(computeSlice(nodeCriterion, localVariableCriterion));
		this.remainingNodes = new TreeSet<PDGNode>();
		remainingNodes.add(pdg.getEntryNode());
		for(GraphNode node : pdg.nodes) {
			PDGNode pdgNode = (PDGNode)node;
			if(!sliceNodes.contains(pdgNode))
				remainingNodes.add(pdgNode);
		}
		this.passedParameters = new LinkedHashSet<VariableDeclaration>();
		Set<PDGNode> nCD = new LinkedHashSet<PDGNode>();
		Set<PDGNode> nDD = new LinkedHashSet<PDGNode>();
		for(GraphEdge edge : pdg.edges) {
			PDGDependence dependence = (PDGDependence)edge;
			PDGNode srcPDGNode = (PDGNode)dependence.src;
			PDGNode dstPDGNode = (PDGNode)dependence.dst;
			if(dependence instanceof PDGDataDependence) {
				PDGDataDependence dataDependence = (PDGDataDependence)dependence;
				if(remainingNodes.contains(srcPDGNode) && sliceNodes.contains(dstPDGNode))
					passedParameters.add(dataDependence.getData());
				if(sliceNodes.contains(srcPDGNode) && remainingNodes.contains(dstPDGNode) &&
						!dataDependence.getData().equals(localVariableCriterion))
					nDD.add(dstPDGNode);
			}
			else if(dependence instanceof PDGControlDependence) {
				if(sliceNodes.contains(srcPDGNode) && remainingNodes.contains(dstPDGNode))
					nCD.add(dstPDGNode);
			}
		}
		Set<PDGNode> controlIndispensableNodes = new LinkedHashSet<PDGNode>();
		for(PDGNode p : nCD) {
			for(VariableDeclaration usedVariable : p.usedVariables) {
				Set<PDGNode> pSliceNodes = computeSlice(p, usedVariable);
				for(GraphNode node : pdg.nodes) {
					PDGNode q = (PDGNode)node;
					if(pSliceNodes.contains(q) || q.equals(p))
						controlIndispensableNodes.add(q);
				}
			}
		}
		Set<PDGNode> dataIndispensableNodes = new LinkedHashSet<PDGNode>();
		for(PDGNode p : nDD) {
			for(VariableDeclaration definedVariable : p.definedVariables) {
				Set<PDGNode> pSliceNodes = computeSlice(p, definedVariable);
				for(GraphNode node : pdg.nodes) {
					PDGNode q = (PDGNode)node;
					if(pSliceNodes.contains(q))
						dataIndispensableNodes.add(q);
				}
			}
		}
		this.indispensableNodes = new TreeSet<PDGNode>();
		indispensableNodes.addAll(controlIndispensableNodes);
		indispensableNodes.addAll(dataIndispensableNodes);
		this.removableNodes = new LinkedHashSet<PDGNode>();
		for(GraphNode node : pdg.nodes) {
			PDGNode pdgNode = (PDGNode)node;
			if(!remainingNodes.contains(pdgNode) && !indispensableNodes.contains(pdgNode))
				removableNodes.add(pdgNode);
		}
	}

	public MethodObject getMethod() {
		return method;
	}

	public PDGNode getExtractedMethodInvocationInsertionNode() {
		return boundaryBlock.getLeader().getPDGNode();
	}

	public PDGNode getNodeCriterion() {
		return nodeCriterion;
	}

	public VariableDeclaration getLocalVariableCriterion() {
		return localVariableCriterion;
	}

	public Set<PDGNode> getSliceNodes() {
		return sliceNodes;
	}

	public Set<VariableDeclaration> getPassedParameters() {
		return passedParameters;
	}

	public Set<PDGNode> getRemovableNodes() {
		return removableNodes;
	}

	public boolean containsDeclarationOfVariableCriterion() {
		for(PDGNode pdgNode : sliceNodes) {
			AbstractStatement statement = pdgNode.getStatement();
			for(LocalVariableDeclarationObject variableDeclaration : statement.getLocalVariableDeclarations()) {
				if(variableDeclaration.getVariableDeclaration().equals(localVariableCriterion))
					return true;
			}
		}
		return false;
	}

	public boolean nodeCritetionIsDeclarationOfVariableCriterion() {
		if(nodeCriterion.declaresLocalVariable(localVariableCriterion))
			return true;
		return false;
	}

	public boolean variableCriterionIsReturnedVariableInOriginalMethod() {
		if(returnedVariablesInOriginalMethod.contains(localVariableCriterion))
			return true;
		return false;
	}

	public boolean containsDuplicateNodeWithStateChangingMethodInvocation() {
		Set<PDGNode> duplicatedNodes = new LinkedHashSet<PDGNode>();
		duplicatedNodes.addAll(sliceNodes);
		duplicatedNodes.retainAll(indispensableNodes);
		for(PDGNode node : duplicatedNodes) {
			for(VariableDeclaration stateChangingVariable : node.getStateChangingVariables()) {
				if(!sliceContainsDeclaration(stateChangingVariable))
					return true;
			}
		}
		return false;
	}

	private boolean sliceContainsDeclaration(VariableDeclaration variableDeclaration) {
		for(PDGNode pdgNode : sliceNodes) {
			AbstractStatement statement = pdgNode.getStatement();
			for(LocalVariableDeclarationObject declaration : statement.getLocalVariableDeclarations()) {
				if(declaration.getVariableDeclaration().equals(variableDeclaration))
					return true;
			}
		}
		return false;
	}

	private Set<PDGNode> computeSlice(PDGNode nodeCriterion, VariableDeclaration localVariableCriterion) {
		Set<PDGNode> sliceNodes = new LinkedHashSet<PDGNode>();
		if(nodeCriterion.definesLocalVariable(localVariableCriterion)) {
			sliceNodes.addAll(traverseBackward(nodeCriterion, new LinkedHashSet<PDGNode>()));
		}
		else if(nodeCriterion.usesLocalVariable(localVariableCriterion)) {
			Set<PDGNode> defNodes = getDefNodes(nodeCriterion, localVariableCriterion);
			for(PDGNode defNode : defNodes) {
				sliceNodes.addAll(traverseBackward(defNode, new LinkedHashSet<PDGNode>()));
			}
		}
		return sliceNodes;
	}

	private Set<PDGNode> getDefNodes(PDGNode node, VariableDeclaration localVariable) {
		Set<PDGNode> defNodes = new LinkedHashSet<PDGNode>();
		for(GraphEdge edge : node.incomingEdges) {
			PDGDependence dependence = (PDGDependence)edge;
			if(edges.contains(dependence) && dependence instanceof PDGDataDependence) {
				PDGDataDependence dataDependence = (PDGDataDependence)dependence;
				if(dataDependence.getData().equals(localVariable)) {
					PDGNode srcPDGNode = (PDGNode)dependence.src;
					defNodes.add(srcPDGNode);
				}
			}
		}
		return defNodes;
	}

	private Set<PDGNode> traverseBackward(PDGNode node, Set<PDGNode> visitedNodes) {
		Set<PDGNode> sliceNodes = new LinkedHashSet<PDGNode>();
		sliceNodes.add(node);
		visitedNodes.add(node);
		for(GraphEdge edge : node.incomingEdges) {
			PDGDependence dependence = (PDGDependence)edge;
			if(edges.contains(dependence)) {
				PDGNode srcPDGNode = (PDGNode)dependence.src;
				if(!visitedNodes.contains(srcPDGNode))
					sliceNodes.addAll(traverseBackward(srcPDGNode, visitedNodes));
			}
		}
		return sliceNodes;
	}

	public String toString() {
		return "<" + localVariableCriterion + ", " + nodeCriterion.getId() + "> [B" + boundaryBlock.getId() + "]\n" +
		sliceNodes + "\npassed parameters: " + passedParameters + "\nindispensable nodes: " + indispensableNodes;
	}
}
