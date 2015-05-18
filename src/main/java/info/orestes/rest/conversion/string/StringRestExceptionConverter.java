package info.orestes.rest.conversion.string;

import info.orestes.rest.conversion.Accept;
import info.orestes.rest.conversion.Context;
import info.orestes.rest.conversion.Converter;
import info.orestes.rest.conversion.MediaType;
import info.orestes.rest.error.RestException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

@Accept(MediaType.TEXT_PLAIN)
public class StringRestExceptionConverter extends Converter<RestException, String> {
	
	@Override
	public String toFormat(Context context, RestException source, Class<?>[] genericParams) {
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		
		printWriter.print(source.getStatusCode());
		printWriter.print(" ");
		printWriter.println(source.getReason());
		printWriter.println();
		printWriter.println(source.getMessage());
		printWriter.println();
		printWriter.println();
		
		source.printStackTrace(printWriter);
		
		return result.toString();
	}
	
	@Override
	public RestException toObject(Context context, String source, Class<?>[] genericParams) {
		throw new UnsupportedOperationException();
	}
}
