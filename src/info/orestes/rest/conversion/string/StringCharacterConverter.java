package info.orestes.rest.conversion.string;

import info.orestes.rest.conversion.Context;
import info.orestes.rest.conversion.Converter;
import info.orestes.rest.conversion.format.StringFormat;

public class StringCharacterConverter extends Converter<Character, String> {
	
	public StringCharacterConverter() {
		super(StringFormat.TEXT_PLAIN);
	}
	
	@Override
	public String toFormat(Context context, Character source, Class<?>... genericParams) {
		return source.toString();
	}
	
	@Override
	public Character toObject(Context context, String source, Class<?>... genericParams) {
		return source.charAt(0);
	}
	
}
