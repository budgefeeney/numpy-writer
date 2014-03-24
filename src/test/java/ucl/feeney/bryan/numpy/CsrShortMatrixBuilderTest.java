package ucl.feeney.bryan.numpy;

import static ucl.feeney.bryan.numpy.CsrMatrixWriter.pythonPath;
import static ucl.feeney.bryan.numpy.CsrMatrixWriter.shellExec;
import static org.junit.Assert.assertEquals;
import it.unimi.dsi.fastutil.ints.Int2ShortMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

/**
 * This class actually launches and executes a Python program. So make sure you have 
 * a python installation on your PATH which in turn has access to numpy and scipy.
 * @author bryanfeeney
 *
 */
public class CsrShortMatrixBuilderTest
{
	private final static short[][] MATRIX = new short[][] {
		new short[] { 2, 3, 0, 0, 0, 1234, 0,   1 },
		new short[] { 0, 0, 0, 0, 0, 0,    0,   0 },
		new short[] { 1, 0, 0, 0, 0, 0,    0,   0 },
		new short[] { 0, 0, 0, 0, 0, 0,    0,   9 },
		new short[] {-1, 0, 0, 2, 0, 0,    0, -98 }
	};
	
	private final static String EXPECTED_OUTPUT = 
		   "[[   2    3    0    0    0 1234    0    1]\n"
		 + " [   0    0    0    0    0    0    0    0]\n"
		 + " [   1    0    0    0    0    0    0    0]\n"
		 + " [   0    0    0    0    0    0    0    9]\n"
		 + " [  -1    0    0    2    0    0    0  -98]]";
	
	private final static String EXPECTED_SPARSE_OUTPUT = 
			  "(0, 5)	1234\n"
			+ "  (0, 0)	2\n"
			+ "  (0, 7)	1\n"
			+ "  (0, 1)	3\n"
			+ "  (2, 0)	1\n"
			+ "  (3, 7)	9\n"
			+ "  (4, 3)	2\n"
			+ "  (4, 0)	-1\n"
			+ "  (4, 7)	-98";
	
	private final static String PY_PARTS_SCRIPT = 
		   "import numpy as np; "
		 + "import scipy.sparse as ssp; "

		 + "indices = np.load('%s'); "
		 + "indptr  = np.load('%s'); "
		 + "data    = np.load('%s'); "
		
		 + "mat = ssp.csr_matrix((data, indices, indptr)); "
		 + "print (mat.toarray()); ";
	
	
	private final static String PY_SINGLE_SCRIPT = 
		   "import numpy as np; "
		 + "import scipy.sparse as ssp; "

		 + "mat = np.load('%s'); "
		 + "print (mat); ";
	
	private static Int2ShortMap toSparseVector (short[] values)
	{	Int2ShortMap map = new Int2ShortOpenHashMap(values.length / 2);
		for (int i = 0; i < values.length; i++)
			if (values[i] != 0)
				map.put(i, values[i]);
				
		return map;
	}
	
	@Test
	public void testMatrix() throws IOException, Exception
	{	
		Path tmpFile = Files.createTempFile("burble", "");
		tmpFile.toFile().deleteOnExit();
		tmpFile = tmpFile.getParent().resolve("matrix");
//		Path tmpFile = Paths.get("/Users/bryanfeeney/Desktop/matrix");
		
		CsrShortMatrixBuilder bldr = new CsrShortMatrixBuilder(MATRIX[0].length);
		for (short[] row : MATRIX)
			bldr.addRow(toSparseVector(row));
		Path[] paths = bldr.writeToFiles(tmpFile);
		assertEquals (3, paths.length);
		
		System.out.println ("Wrote matrix to " + tmpFile.getParent());
		
		String pyscript = String.format (PY_PARTS_SCRIPT, paths[0].toString(), paths[1].toString(), paths[2].toString());
		Pair<String, String> output = shellExec (new String[] { pythonPath(), "-c", pyscript });
		
		System.out.println ("Stderr: \n" + output.getRight());
		System.out.println ("Stdout: \n" + output.getLeft());
		
		assertEquals ("", output.getRight().trim());
		assertEquals (EXPECTED_OUTPUT, output.getLeft().trim());
		
		
		// ------------------------
		
		paths = bldr.writeToFile(tmpFile);
		assertEquals (1, paths.length);
		System.out.println ("Wrote single file to " + paths[0]);
		
		pyscript = String.format (PY_SINGLE_SCRIPT, paths[0].toString());
		output = shellExec (new String[] { pythonPath(), "-c", pyscript });
		
		System.out.println ("Stderr: \n" + output.getRight());
		System.out.println ("Stdout: \n" + output.getLeft());
		
		assertEquals ("", output.getRight().trim());
		assertEquals (EXPECTED_SPARSE_OUTPUT, output.getLeft().trim());
	}
	

}
