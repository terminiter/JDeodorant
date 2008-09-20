package gr.uom.java.ast.decomposition.cfg;

import org.eclipse.jdt.core.dom.VariableDeclaration;

public class PDGDataDependence extends PDGDependence {
	private VariableDeclaration data;
	private volatile int hashCode = 0;
	
	public PDGDataDependence(PDGNode src, PDGNode dst, VariableDeclaration data) {
		super(src, dst);
		this.data = data;
		src.addOutgoingEdge(this);
		dst.addIncomingEdge(this);
	}

	public VariableDeclaration getData() {
		return data;
	}

	public boolean equals(Object o) {
		if(this == o)
    		return true;
		
		if(o instanceof PDGDataDependence) {
			PDGDataDependence dataDependence = (PDGDataDependence)o;
			return this.src.equals(dataDependence.src) && this.dst.equals(dataDependence.dst) &&
				this.data.equals(dataDependence.data);
		}
		return false;
	}

	public int hashCode() {
		if(hashCode == 0) {
			int result = 17;
			result = 37*result + src.hashCode();
			result = 37*result + dst.hashCode();
			result = 37*result + data.hashCode();
			hashCode = result;
		}
		return hashCode;
	}

	public String toString() {
		return src.toString() + "-->" + data.toString() + "\n" + dst.toString();
	}
}
