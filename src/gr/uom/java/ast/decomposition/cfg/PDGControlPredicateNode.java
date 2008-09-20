package gr.uom.java.ast.decomposition.cfg;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.VariableDeclaration;

import gr.uom.java.ast.LocalVariableDeclarationObject;
import gr.uom.java.ast.LocalVariableInstructionObject;
import gr.uom.java.ast.decomposition.AbstractExpression;
import gr.uom.java.ast.decomposition.CompositeStatementObject;

public class PDGControlPredicateNode extends PDGNode {
	private Set<VariableDeclaration> variableDeclarations;
	
	public PDGControlPredicateNode(CFGNode cfgNode, Set<VariableDeclaration> variableDeclarations) {
		super(cfgNode);
		this.variableDeclarations = variableDeclarations;
		determineDefinedAndUsedVariables();
	}

	private void determineDefinedAndUsedVariables() {
		CFGNode cfgNode = getCFGNode();
		if(cfgNode.getStatement() instanceof CompositeStatementObject) {
			CompositeStatementObject composite = (CompositeStatementObject)cfgNode.getStatement();
			List<AbstractExpression> expressions = composite.getExpressions();
			for(AbstractExpression expression : expressions) {
				List<LocalVariableDeclarationObject> variableDeclarations = expression.getLocalVariableDeclarations();
				for(LocalVariableDeclarationObject variableDeclaration : variableDeclarations)
					definedVariables.add(variableDeclaration.getVariableDeclaration());
				List<LocalVariableInstructionObject> variableInstructions = expression.getLocalVariableInstructions();
				for(LocalVariableInstructionObject variableInstruction : variableInstructions) {
					VariableDeclaration variableDeclaration = getVariableDeclaration(variableInstruction);
					List<Assignment> assignments = expression.getLocalVariableAssignments(variableInstruction);
					List<PostfixExpression> postfixExpressions = expression.getLocalVariablePostfixAssignments(variableInstruction);
					List<PrefixExpression> prefixExpressions = expression.getLocalVariablePrefixAssignments(variableInstruction);
					if(!assignments.isEmpty()) {
						definedVariables.add(variableDeclaration);
						for(Assignment assignment : assignments) {
							Assignment.Operator operator = assignment.getOperator();
							if(!operator.equals(Assignment.Operator.ASSIGN))
								usedVariables.add(variableDeclaration);
						}
					}
					else if(!postfixExpressions.isEmpty()) {
						definedVariables.add(variableDeclaration);
						usedVariables.add(variableDeclaration);
					}
					else if(!prefixExpressions.isEmpty()) {
						definedVariables.add(variableDeclaration);
						usedVariables.add(variableDeclaration);
					}
					else
						usedVariables.add(variableDeclaration);
				}
			}
		}
	}
	
	private VariableDeclaration getVariableDeclaration(LocalVariableInstructionObject variableInstruction) {
		for(VariableDeclaration variableDeclaration : variableDeclarations) {
			if(variableDeclaration.resolveBinding().isEqualTo(variableInstruction.getSimpleName().resolveBinding()))
				return variableDeclaration;
		}
		return null;
	}
}
