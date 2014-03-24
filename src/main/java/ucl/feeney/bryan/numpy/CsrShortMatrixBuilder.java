package ucl.feeney.bryan.numpy;

import it.unimi.dsi.fastutil.ints.Int2ShortMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Creates a class emulating a Scipy CSR sparse matrix where the data are 16-bit signed ints.
 * <p>
 * This is implemented to make it easier to build such a matrix and then subsequently write it
 * out (see {@link CsrMatrixWriter}). It's not suitable for any sort of arithmetic.
 */
public class CsrShortMatrixBuilder
{
	private final IntList indices;
	private final IntList indptr; // points to the start of each row in the indices
	private final ShortList data; // the actual non-zero values.
	
	private int rows;
	private int cols;
	
	/**
	 * Creates a new builder
	 * @param cols the actual number of columns in this matrix
	 * @param rowSizeHint a guess as to how many rows there will be
	 * @param nonZeroColSizeHint a guess as to how many non-zero entries there
	 * will be per row.
	 */
	public CsrShortMatrixBuilder(int cols, int rowSizeHint, int nonZeroColSizeHint)
	{	this.cols = cols;
		this.rows = 0;
		
		indices = new IntArrayList(rowSizeHint * nonZeroColSizeHint);
		indptr  = new IntArrayList(rowSizeHint);
		data    = new ShortArrayList(rowSizeHint * nonZeroColSizeHint); 
		
		indptr.add (indices.size()); // indptr has always got one more entry than the
	}                                // number of rows, such that the last entry is the
	                                 // effectively the length of the indices / data lists.
	
	/**
	 * Creates a new bulider
	 * @param cols the actual number of columns in this matrix
	 */
	public CsrShortMatrixBuilder(int cols)
	{	this.cols = cols;
		
		indices = new IntArrayList();
		indptr  = new IntArrayList();
		data    = new ShortArrayList();
		
		indptr.add (indices.size()); // indptr has always got one more entry than the
	}                                // number of rows, such that the last entry is the
	                                 // effectively the length of the indices / data lists.
	/**
	 * Adds a row to this matrix. Values are copied over, so the vector
	 * object can be cleared and re-used.
	 * @param vector
	 */
	public void addRow (Int2ShortMap vector)
	{	for (Int2ShortMap.Entry entry : vector.int2ShortEntrySet())
		{	indices.add (entry.getIntKey());
			data.add (entry.getShortValue());
		}
		indptr.add (indices.size());
		++rows;
	}

	public IntList getIndices()
	{	return indices;
	}

	public IntList getIndptr()
	{	return indptr;
	}

	public ShortList getData()
	{	return data;
	}
	
	public int getRows()
	{	return rows;
	}
	
	public int getCols()
	{	return cols;
	}
	
	/**
	 * Writes out three files which can be used to reconstitute a single Scipy sparse CSR matrix
	 * This are the indices, the indptr and the data. Provide a file prefix, and three files
	 * will be created, each with the numpy array in the appropriate NPY format.
	 * @param filePrefix the filename prefix used for all these files.
	 * @return the paths to the indices, indptr and data files in that order.
	 * @throws Exception 
	 * @throws IOException 
	 */
	public Path[] writeToFiles (Path filePrefix) throws Exception
	{	try (CsrMatrixWriter wtr = new CsrMatrixWriter (filePrefix, /* combine = */ false))
		{	return wtr.writeCsrShortMatrix(this);
		}
	}
	
	/**
	 * Writes out this to a single CSR sparse matrix file. Note that this requires us
	 * to write out temporary files, then execute a Python script, so this will use
	 * <emph>twice</emph> as much memory as {@link #writeToFiles(Path)}
	 * will be created, each with the numpy array in the appropriate NPY format.
	 * @param filePrefix the filename prefix used for all these files.
	 * @return the paths to the indices, indptr and data files in that order.
	 * @throws Exception 
	 * @throws IOException 
	 */
	public Path[] writeToFile (Path filePrefix) throws Exception
	{	try (CsrMatrixWriter wtr = new CsrMatrixWriter (filePrefix, /* combine = */ true))
		{	return wtr.writeCsrShortMatrix(this);
		}
	}
}
