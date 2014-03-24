package ucl.feeney.bryan.numpy;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.shorts.ShortList;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A slightly hacky class that writes out a sparse matrix to disk in a numpy compatible
 * format. Typically this is in the three elements that can be used to construct
 * a numpy sparse CSR matrix: the indices, the ind_ptr, and data. The data is stored as
 * 16-bit integers for compactness.
 * <p>
 * If you want, you can have this launch a python script which will simply read in the
 * three individual files, combined them into a single matrix, then write out again. 
 * However note that this could take quite some time, as the whole matrix has to be
 * loaded into memory!
 * <p>
 * For true big-data problems we'd write out a bunch of sparse vectors instead to a single
 * large file, say, and load them in one at a time via memmap.However for experimentation,
 * this brutish approach should work fine.
 */
class CsrMatrixWriter implements AutoCloseable
{
	private final static Logger LOG = LoggerFactory.getLogger(CsrMatrixWriter.class);
	
	// There are better ways of doing this, but none are worth my time...
	public final static String PYTHON_PATH = "/opt/local/bin/python3";
	
	private final static String INDICES = "-indices.npy";
	private final static String INDPTR  = "-indptr.npy";
	private final static String DATA    = "-data.npy";
	
	private final static int BLOCK_SIZE = 16;
	
	private final static byte[] NPY_HEADER;
	static {
		byte[] hdr = "XNUMPY".getBytes(Charsets.US_ASCII);
		hdr[0] = (byte) 0x93;
		NPY_HEADER = hdr;
	}
	
	private final static byte   NPY_MAJ_VERSION = 1;
	private final static byte   NPY_MIN_VERSION = 0;
	
	/** 
	 * A python script used to load int th intermediate array files,
	 * combine them into a single scipy.sparse matrix, then save that
	 * out.
	 */
	private final static String PY_COMBINE_SCRIPT = 
		   "import numpy as np; "
		 + "import scipy.sparse as ssp; "
		 + "import pickle as pkl; "

		 + "indices = np.load('%s'); "
		 + "indptr  = np.load('%s'); "
		 + "data    = np.load('%s'); "
		
		 + "mat = ssp.csr_matrix((data, indices, indptr)); "
		 + "f = open('%s', 'wb'); "
     + "pkl.dump(mat, f); "
     + "f.close(); ";
	
	/** 
	 * if true launch a Python script on close which combines the three outputs into
	 * a single matrix and deletes the intermeida files
	 */
	private final boolean recombine;
	
	/**
	 * The path, including the file-name. This is essentially a prefix to which
	 * "-indices.npy", "-indptr.npy" and "-data.npy" will be appended. If 
	 * recombine is set to true, these intermediate files will be deleted, and
	 * this becomes a prefix to which just ".npy" is appended
	 */
	private final Path filePrefix;
	
	private Path                 indicesPath;
	private BufferedOutputStream indices;
	
	private Path                 indptrPath;
	private BufferedOutputStream indptr;
	
	private Path                 dataPath;
	private BufferedOutputStream data;
	
	/**
	 * Creates a new writer. Throws an exception if one or more of the files cannot be
	 * created.
	 * @param filePrefix the prefix of all the files that will be created
	 * @param recombine if true after all the files have been created and populated
	 * we launch a Python script to load them in, merged them into a single CSR matrix
	 * object, and write them out again.
	 */
	public CsrMatrixWriter(Path filePrefix, boolean recombine) throws IOException {
		super();
		this.filePrefix = filePrefix;
		this.recombine = recombine;
		
		openStreams();
	}

	/**
	 * Open the outputstreams for the three files that constitute a Scipy
	 * sparse CSR matrix object.
	 * @throws IOException 
	 */
	private void openStreams() throws IOException
	{
		indicesPath = appendFileNameSuffix(filePrefix, INDICES);
		indptrPath  = appendFileNameSuffix(filePrefix, INDPTR);
		dataPath    = appendFileNameSuffix(filePrefix, DATA);
		
		indices = newBufferedOutputStream(indicesPath);
		indptr  = newBufferedOutputStream(indptrPath);
		data    = newBufferedOutputStream(dataPath);
	}

	private BufferedOutputStream newBufferedOutputStream(Path path) throws IOException {
		return new BufferedOutputStream (Files.newOutputStream (path));
	}

	
	/**
	 * Writes out the three data-structures required of a sparse scipy array and
	 * into three separate files. See class documentation for more on this.
	 * @param csr the matrix to write out
	 * @return the paths to the indices, indptr and data files in that order.
	 * @throws Exception 
	 */
	public Path[] writeCsrShortMatrix (CsrShortMatrixBuilder csr) throws Exception
	{	writeNumpyArray (indices, csr.getIndices());
		writeNumpyArray (indptr,  csr.getIndptr());
		writeNumpyArray (data,    csr.getData());
		
		if (! recombine)
			return new Path[] { indicesPath, indptrPath, dataPath };
	
		// otherwise launch a Python script to build a sparse matrix from 
		// these three files, and then use Python itself to save that sparse
		// matrix out as a single file, finally deleting the intermediate files.
		//
		// Note that this essentially _doubles_ the memory usage
		
		close();
//		indicesPath.toFile().deleteOnExit();
//		indptrPath.toFile().deleteOnExit();
//		dataPath.toFile().deleteOnExit();
		
		Path combinedPath = appendFileNameSuffix(filePrefix, ".pkl");
		String pyScript   = String.format (
			PY_COMBINE_SCRIPT,
			indicesPath.toString(),
			indptrPath.toString(),
			dataPath.toString(),
			combinedPath.toString()
		);
		
		try
		{	Pair<String, String> output = shellExec (new String[] { pythonPath(), "-c", pyScript });
			String err = output.getRight().trim();
			if (! err.isEmpty())
				throw new IOException ("Failed to combine arrays into a matrix due to script error: " + err);
			
			return new Path[] { combinedPath };
		}
		catch (InterruptedException ie)
		{	throw new IOException ("Interrupted while waiting for the Python recombination script to finish: " + ie.getMessage(), ie);
		}
	}

	final static String pythonPath()
	{
		for (int v = 3; v >= 0; v--)
		{	Path p = Paths.get(PYTHON_PATH + '.' + v);
			if (Files.exists(p))
				return p.toString();
		}
		
		return PYTHON_PATH;
	}
	
	/**
	 * Executes the given commandline. Returns a tuple with the consequent 
	 * stdout and stderr in that order
	 * @param the command line to execute
	 * @param a tuple containing the captured stdout and stderr in that order.
	 */
	static Pair<String, String> shellExec (String[] cmdline) throws IOException, InterruptedException
	{	LOG.info("Launching command " + StringUtils.join (cmdline, ' '));
	
		Process p = Runtime.getRuntime().exec(cmdline);
		p.waitFor();
		
		String err, out;
		
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream())); )
		{	StringBuilder buf = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null)
				buf.append (line).append('\n');
			
			err = buf.toString();
		}
 
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream())); )
		{	StringBuilder buf = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null)
				buf.append (line).append('\n');
			
			out = buf.toString();
		}
		
		return Pair.of(out, err);
	}
	
	/**
	 * Writes out a numpy array to a file, including the header.
	 * @param arr the array to write out
	 * @throws IOException 
	 */
	private void writeNumpyArray (OutputStream out, IntList array) throws IOException
	{	writeHeader (out, Integer.TYPE, array.size());
		for (int i = 0; i < array.size(); i++)
			writeLEInt (out, array.get(i));
	}
	
	/**
	 * Writes out a numpy array to a file, including the header.
	 * @param arr the array to write out
	 * @throws IOException 
	 */
	private void writeNumpyArray (OutputStream out, ShortList array) throws IOException
	{	writeHeader (out, Short.TYPE, array.size());
		for (int i = 0; i < array.size(); i++)
			writeLEShort (out, array.get(i));
	}
	
	/**
	 * Writes out a standard numpy header, with the standard data type. Don't
	 * call this directly, it's called already by {@link #writeNumpyArray(OutputStream, IntList)}
	 * @param out the write to write to. We assume this is an ASCII writer.
	 * @param datatype acceptable values for this are currently Integer.class
	 * and Short.class
	 * @param len length of the numpy array to be stored.
	 */
	private void writeHeader (OutputStream out, Class<?> datatype, int len) throws IOException
	{
		// Magic number and format version.
		out.write(NPY_HEADER);
		out.write(NPY_MAJ_VERSION);
		out.write(NPY_MIN_VERSION);
		
		// Describes the data to be written. Padded with space characteres to be an even
		// multiple of the block size. Terminated with a newline. Prefixed with a header length.
		String dataHeader = 
			  "{ 'descr': '" + toDataTypeStr (datatype)
			+ "', 'fortran_order': False"
			+ ", 'shape': (" + len + ",), "
			+ "}";
		int hdrLen    = dataHeader.length() + 1; // +1 for a terminating newline.
		int minHdrLen = hdrLen + (hdrLen % BLOCK_SIZE);
		
		dataHeader = dataHeader + StringUtils.repeat(' ', minHdrLen - hdrLen) + '\n';
		
		writeLEShort (out, (short) minHdrLen);
		out.write (dataHeader.getBytes(Charsets.US_ASCII));
	}
	
	/**
	 * Writes a little-endian short to the given output stream
	 * @param out the stream
	 * @param value the short value
	 * @throws IOException 
	 */
	private void writeLEShort (OutputStream out, short value) throws IOException
	{	out.write ( value        & 0x00FF);
		out.write ((value >> 8)  & 0x00FF);
	}	
	
	/**
	 * Writes a little-endian int to the given output stream
	 * @param out the stream
	 * @param value the short value
	 * @throws IOException 
	 */
	public static void writeLEInt(OutputStream out, int value) throws IOException
	{
		out.write(value & 0xFF);
		out.write((value >> 8) & 0xFF);
		out.write((value >> 16) & 0xFF);
		out.write((value >> 24) & 0xFF);
	}
	
	/**
	 * Converts a Java class to a python datatype String. Currently only Integer
	 * and Short are supported.
	 */
	private String toDataTypeStr(Class<?> datatype)
	{
		if (datatype == Integer.class || datatype == Integer.TYPE)
			return "<i4";
		else if (datatype == Short.class || datatype == Short.TYPE)
			return "<i2";
		else
			throw new IllegalArgumentException("Don't know the corresponding Python datatype for " + datatype.getSimpleName());
	}


	/**
	 * Tries to close all three streams in use. Note that exceptions may get lost
	 * if two or more streams throw an exception while closing.
	 */
	public void close() throws Exception
	{	Exception err  = close (indices);
		err = MergedException.merge (err, close (indptr));
		err = MergedException.merge (err, close(data));
		
		if (err != null)
			throw err;
	}
	
	private IOException close (Closeable c)
	{	try
		{	c.close();
			return null;
		}
		catch (IOException ioe)
		{	return ioe;
		}
	}
	
	/**
	 * Assuming the given path is meant to resolve to a file, creates a new path
	 * which resolves to a file with the same name except that a suffix has been
	 * added, so "/home/bfeeney/dat", ".txt" becomes "/home/bfeeney/dat.txt"
	 */
	public static Path appendFileNameSuffix(Path path, String suffix) {
		return path.getParent().resolve(path.getFileName().toString() + suffix);
	}

}
