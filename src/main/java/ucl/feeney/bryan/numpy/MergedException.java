package ucl.feeney.bryan.numpy;

import java.sql.SQLException;

/**
 * A class which merges exceptions together, so that a method which has
 * encountered several exceptions (e.g one in a try block, then another
 * in a finally block) can report both exceptions.
 * <p> 
 * Use the static {@link #merge(Exception, Exception)} method
 * to merge exceptions together.
 * @author bryanfeeney
 *
 */
public class MergedException extends SQLException
{
	private static final long serialVersionUID = 1L;

	public MergedException() {
		super();
	}

	public MergedException(String reason, String sqlState, int vendorCode,
			Throwable cause) {
		super(reason, sqlState, vendorCode, cause);
	}

	public MergedException(String reason, String SQLState, int vendorCode) {
		super(reason, SQLState, vendorCode);
	}

	public MergedException(String reason, String sqlState, Throwable cause) {
		super(reason, sqlState, cause);
	}

	public MergedException(String reason, String SQLState) {
		super(reason, SQLState);
	}

	public MergedException(String reason, Throwable cause) {
		super(reason, cause);
	}

	public MergedException(String reason) {
		super(reason);
	}

	public MergedException(Throwable cause) {
		super(cause);
	}
	
	/**
	 * Merges two exceptoins into a single one: e.g.  if you have to close multiple
	 * streams in a single method, and you want to attempt to close them all, then
	 * throw a single exception if one ore more failed to close;
	 * <p>
	 * Exceptions on either side may be null. If both are null, then null is returned.
	 */
	public static Exception merge (Exception lhs, Exception rhs)
	{	if (lhs == null && rhs == null)
			return null;
		if (lhs != null && rhs == null)
			return lhs;
		if (lhs == null && rhs != null)
			return rhs;
		
		// both are non-null.
		if (lhs instanceof MergedException)
			((MergedException) lhs).setNextException(new MergedException ("Next error in chain: " + rhs.getMessage(), rhs));
			
		MergedException result = new MergedException ( "Many errors reported. First is " + lhs.getMessage(), lhs);
		result.setNextException(new MergedException ("Next error in chain: " + rhs.getMessage(), rhs));
		
		return result;
	}
}