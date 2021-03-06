/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.source.internal.annotations.global;

import java.util.Collection;
import java.util.HashMap;
import javax.persistence.LockModeType;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;

import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.CacheMode;
import org.hibernate.FlushMode;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.MappingException;
import org.hibernate.annotations.CacheModeType;
import org.hibernate.annotations.FlushModeType;
import org.hibernate.annotations.QueryHints;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.engine.query.spi.sql.NativeSQLQueryRootReturn;
import org.hibernate.engine.spi.NamedQueryDefinitionBuilder;
import org.hibernate.engine.spi.NamedSQLQueryDefinition;
import org.hibernate.engine.spi.NamedSQLQueryDefinitionBuilder;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.LockModeConverter;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.metamodel.source.internal.annotations.AnnotationBindingContext;
import org.hibernate.metamodel.source.internal.annotations.util.HibernateDotNames;
import org.hibernate.metamodel.source.internal.annotations.util.JPADotNames;
import org.hibernate.metamodel.source.internal.annotations.util.JandexHelper;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.logging.Logger;

/**
 * Binds {@link NamedQuery}, {@link NamedQueries}, {@link NamedNativeQuery}, {@link NamedNativeQueries},
 * {@link org.hibernate.annotations.NamedQuery}, {@link org.hibernate.annotations.NamedQueries},
 * {@link org.hibernate.annotations.NamedNativeQuery}, and {@link org.hibernate.annotations.NamedNativeQueries}.
 *
 * @author Hardy Ferentschik
 */
public class QueryProcessor {

	private static final CoreMessageLogger LOG = Logger.getMessageLogger(
			CoreMessageLogger.class,
			QueryProcessor.class.getName()
	);

	private QueryProcessor() {
	}

	/**
	 * Binds all {@link NamedQuery}, {@link NamedQueries}, {@link NamedNativeQuery}, {@link NamedNativeQueries},
	 * {@link org.hibernate.annotations.NamedQuery}, {@link org.hibernate.annotations.NamedQueries},
	 * {@link org.hibernate.annotations.NamedNativeQuery}, and {@link org.hibernate.annotations.NamedNativeQueries}
	 * annotations to the supplied metadata.
	 *
	 * @param bindingContext the context for annotation binding
	 */
	public static void bind(AnnotationBindingContext bindingContext) {
		final ClassLoaderService classLoaderService = bindingContext.getBuildingOptions().getServiceRegistry().getService( ClassLoaderService.class );
		Collection<AnnotationInstance> annotations = JandexHelper.getAnnotations(
				bindingContext.getIndex(),
				JPADotNames.NAMED_QUERY,
				JPADotNames.NAMED_QUERIES,
				classLoaderService
		);
		for ( AnnotationInstance query : annotations ) {
			bindNamedQuery( bindingContext, query );
		}

		annotations = JandexHelper.getAnnotations(
				bindingContext.getIndex(),
				JPADotNames.NAMED_NATIVE_QUERY,
				JPADotNames.NAMED_NATIVE_QUERIES,
				classLoaderService
		);
		for ( AnnotationInstance query : annotations ) {
			bindNamedNativeQuery( query, bindingContext );
		}

		annotations = JandexHelper.getAnnotations(
				bindingContext.getIndex(),
				HibernateDotNames.NAMED_QUERY,
				HibernateDotNames.NAMED_QUERIES,
				classLoaderService
		);
		for ( AnnotationInstance query : annotations ) {
			bindNamedQuery( bindingContext, query );
		}

		annotations = JandexHelper.getAnnotations(
				bindingContext.getIndex(),
				HibernateDotNames.NAMED_NATIVE_QUERY,
				HibernateDotNames.NAMED_NATIVE_QUERIES,
				classLoaderService
		);
		for ( AnnotationInstance query : annotations ) {
			bindNamedNativeQuery( query, bindingContext );
		}
	}

	/**
	 * Binds {@link javax.persistence.NamedQuery} as well as {@link org.hibernate.annotations.NamedQuery}.
	 */
	private static void bindNamedQuery(AnnotationBindingContext bindingContext, AnnotationInstance annotation) {
		final ClassLoaderService classLoaderService = bindingContext.getBuildingOptions().getServiceRegistry().getService( ClassLoaderService.class );
		final String name = JandexHelper.getValue( annotation, "name", String.class, classLoaderService );
		if ( StringHelper.isEmpty( name ) ) {
			throw new AnnotationException( "A named query must have a name when used in class or package level" );
		}
		NamedQueryDefinitionBuilder builder = new NamedQueryDefinitionBuilder();
		builder.setName( name );

		final String query = JandexHelper.getValue( annotation, "query", String.class, classLoaderService );
		builder.setQuery( query );
		if ( annotation.name().equals( JPADotNames.NAMED_QUERY ) ) {
			bindJPANamedQuery( annotation, builder, name, query, bindingContext );
		} else {
			builder.setFlushMode(
					getFlushMode( JandexHelper.getEnumValue( annotation, "flushMode", FlushModeType.class, classLoaderService ) ) )
					.setCacheable( JandexHelper.getValue( annotation, "cacheable", Boolean.class, classLoaderService ) )
					.setCacheRegion( defaultToNull( JandexHelper.getValue( annotation, "cacheRegion", String.class, classLoaderService ) ) )
					.setFetchSize( defaultToNull( JandexHelper.getValue( annotation, "fetchSize", Integer.class, classLoaderService ) ) )
					.setTimeout( defaultToNull( JandexHelper.getValue( annotation, "timeout", Integer.class, classLoaderService ) ) )
					.setComment( JandexHelper.getValue( annotation, "comment", String.class, classLoaderService ) )
					.setCacheMode( getCacheMode( JandexHelper.getValue( annotation, "cacheMode", CacheModeType.class, classLoaderService ) ) )
					.setReadOnly( JandexHelper.getValue( annotation, "readOnly", Boolean.class, classLoaderService ) );
		}


		bindingContext.getMetadataCollector().addNamedQuery(builder.createNamedQueryDefinition());
		LOG.debugf( "Binding named query: %s => %s", name, query );
	}

	public static FlushMode getFlushMode(FlushModeType flushModeType) {
		FlushMode flushMode;
		switch ( flushModeType ) {
			case ALWAYS:
				flushMode = FlushMode.ALWAYS;
				break;
			case AUTO:
				flushMode = FlushMode.AUTO;
				break;
			case COMMIT:
				flushMode = FlushMode.COMMIT;
				break;
			case MANUAL:
				flushMode = FlushMode.MANUAL;
				break;
			case PERSISTENCE_CONTEXT:
				flushMode = null;
				break;
			default:
				throw new AssertionFailure( "Unknown flushModeType: " + flushModeType );
		}
		return flushMode;
	}
	private static CacheMode getCacheMode(CacheModeType cacheModeType) {
		switch ( cacheModeType ) {
			case GET:
				return CacheMode.GET;
			case IGNORE:
				return CacheMode.IGNORE;
			case NORMAL:
				return CacheMode.NORMAL;
			case PUT:
				return CacheMode.PUT;
			case REFRESH:
				return CacheMode.REFRESH;
			default:
				throw new AssertionFailure( "Unknown cacheModeType: " + cacheModeType );
		}
	}


	private static void bindJPANamedQuery(
			AnnotationInstance annotation,
			NamedQueryDefinitionBuilder builder,
			String name,
			String query,
			AnnotationBindingContext bindingContext){
		final ClassLoaderService classLoaderService = bindingContext.getBuildingOptions().getServiceRegistry().getService( ClassLoaderService.class );
		AnnotationInstance[] hints = JandexHelper.getValue( annotation, "hints", AnnotationInstance[].class,
				classLoaderService );

		String cacheRegion = getString( hints, QueryHints.CACHE_REGION, bindingContext );
		if ( StringHelper.isEmpty( cacheRegion ) ) {
			cacheRegion = null;
		}

		Integer timeout = getTimeout( hints, query, bindingContext );
		if ( timeout != null && timeout < 0 ) {
			timeout = null;
		}
		//TODO this 'javax.persistence.lock.timeout' has been mvoed to {@code AvailableSettings} in master
		//we should change this when we merge this branch back.
		Integer lockTimeout =  getInteger( hints, "javax.persistence.lock.timeout" , query, bindingContext );
		lockTimeout = defaultToNull( lockTimeout );
		
		LockOptions lockOptions = new LockOptions( LockModeConverter.convertToLockMode( JandexHelper.getEnumValue(
				annotation,
				"lockMode",
				LockModeType.class,
				classLoaderService
		) ) );
		if ( lockTimeout != null ) {
			lockOptions.setTimeOut( lockTimeout );
		}

		builder.setCacheable( getBoolean( hints, QueryHints.CACHEABLE, name, bindingContext ) )
				.setCacheRegion( cacheRegion )
				.setTimeout( timeout )
				.setLockOptions( lockOptions )
				.setFetchSize( defaultToNull( getInteger( hints, QueryHints.FETCH_SIZE, name, bindingContext ) ) )
				.setFlushMode( getFlushMode( hints, QueryHints.FLUSH_MODE, name, bindingContext ) )
				.setCacheMode( getCacheMode( hints, QueryHints.CACHE_MODE, name, bindingContext ) )
				.setReadOnly( getBoolean( hints, QueryHints.READ_ONLY, name, bindingContext ) )
				.setComment( defaultToNull( getString( hints, QueryHints.COMMENT, bindingContext ) ) )
				.setParameterTypes( null );
	}

	private static void bindNamedNativeQuery(AnnotationInstance annotation, AnnotationBindingContext bindingContext) {
		final ClassLoaderService classLoaderService = bindingContext.getBuildingOptions().getServiceRegistry().getService( ClassLoaderService.class );
		String name = JandexHelper.getValue( annotation, "name", String.class, classLoaderService );
		if ( StringHelper.isEmpty( name ) ) {
			throw new AnnotationException( "A named native query must have a name when used in class or package level" );
		}

		String query = JandexHelper.getValue( annotation, "query", String.class, classLoaderService );

		String resultSetMapping = JandexHelper.getValue( annotation, "resultSetMapping", String.class, classLoaderService );

		AnnotationInstance[] hints = JandexHelper.getValue( annotation, "hints", AnnotationInstance[].class, classLoaderService );

		boolean cacheable = getBoolean( hints, "org.hibernate.cacheable", name, bindingContext );
		String cacheRegion = getString( hints, QueryHints.CACHE_REGION, bindingContext );
		if ( StringHelper.isEmpty( cacheRegion ) ) {
			cacheRegion = null;
		}

		Integer timeout = getTimeout( hints, query, bindingContext );
		if ( timeout != null && timeout < 0 ) {
			timeout = null;
		}

		Integer fetchSize = getInteger( hints, QueryHints.FETCH_SIZE, name, bindingContext );
		if ( fetchSize != null && fetchSize < 0 ) {
			fetchSize = null;
		}

		FlushMode flushMode = getFlushMode( hints, QueryHints.FLUSH_MODE, name, bindingContext );
		CacheMode cacheMode = getCacheMode( hints, QueryHints.CACHE_MODE, name, bindingContext );

		boolean readOnly = getBoolean( hints, QueryHints.READ_ONLY, name, bindingContext );

		String comment = getString( hints, QueryHints.COMMENT, bindingContext );
		if ( StringHelper.isEmpty( comment ) ) {
			comment = null;
		}

		boolean callable = getBoolean( hints, QueryHints.CALLABLE, name, bindingContext );
		NamedSQLQueryDefinition def;
		if ( StringHelper.isNotEmpty( resultSetMapping ) ) {
			boolean resultSetMappingExists = bindingContext.getMetadataCollector().getResultSetMappingDefinitions().containsKey( resultSetMapping );
			if ( !resultSetMappingExists ) {
				throw new MappingException(
						String.format(
								"Named SQL Query [%s] referenced an non-existent result set mapping [%s] ",
								name,
								resultSetMapping
						)
				);
			}
			def = new NamedSQLQueryDefinitionBuilder().setName( name )
					.setQuery( query )
					.setResultSetRef(
							resultSetMapping
					)
					.setQuerySpaces( null )
					.setCacheable( cacheable )
					.setCacheRegion( cacheRegion )
					.setTimeout( timeout )
					.setFetchSize( fetchSize )
					.setFlushMode( flushMode )
					.setCacheMode( cacheMode )
					.setReadOnly( readOnly )
					.setComment( comment )
					.setParameterTypes( null )
					.setCallable( callable )
					.createNamedQueryDefinition();
		}
		else {
			AnnotationValue annotationValue = annotation.value( "resultClass" );
			NativeSQLQueryRootReturn[] queryRoots;
			if ( annotationValue == null ) {
				// pure native scalar query
				queryRoots = new NativeSQLQueryRootReturn[0];
			}
			else {
				queryRoots = new NativeSQLQueryRootReturn[] {
						new NativeSQLQueryRootReturn(
								"alias1",
								annotationValue.asString(),
								new HashMap<String, String[]>(),
								LockMode.READ
						)
				};
			}
			def = new NamedSQLQueryDefinitionBuilder().setName( name )
					.setQuery( query )
					.setQueryReturns( queryRoots )
					.setQuerySpaces( null )
					.setCacheable( cacheable )
					.setCacheRegion( cacheRegion )
					.setTimeout( timeout )
					.setFetchSize( fetchSize )
					.setFlushMode( flushMode )
					.setCacheMode( cacheMode )
					.setReadOnly( readOnly )
					.setComment( comment )
					.setParameterTypes( null )
					.setCallable( callable )
					.createNamedQueryDefinition();
		}
		bindingContext.getMetadataCollector().addNamedNativeQuery( def );
		LOG.debugf( "Binding named native query: %s => %s", name, query );
	}

	private static boolean getBoolean(AnnotationInstance[] hints, String element, String query, AnnotationBindingContext bindingContext) {
		String val = getString( hints, element, bindingContext );
		if ( val == null || val.equalsIgnoreCase( "false" ) ) {
			return false;
		}
		if ( val.equalsIgnoreCase( "true" ) ) {
			return true;
		}
		throw new AnnotationException( "Not a boolean in hint: " + query + ":" + element );
	}

	private static CacheMode getCacheMode(AnnotationInstance[] hints, String element, String query, AnnotationBindingContext bindingContext) {
		String val = getString( hints, element, bindingContext );
		if ( val == null ) {
			return null;
		}
		try {
			return CacheMode.valueOf( val.toUpperCase() );
		}
		catch ( IllegalArgumentException e ) {
			throw new AnnotationException( "Unknown CacheMode in hint: " + query + ":" + element );
		}
	}

	private static FlushMode getFlushMode(AnnotationInstance[] hints, String element, String query, AnnotationBindingContext bindingContext) {
		String val = getString( hints, element, bindingContext );
		if ( val == null ) {
			return null;
		}
		try {
			return FlushMode.valueOf( val.toUpperCase() );
		}
		catch ( IllegalArgumentException e ) {
			throw new AnnotationException( "Unknown FlushMode in hint: " + query + ":" + element );
		}
	}

	private static Integer getInteger(AnnotationInstance[] hints, String element, String query, AnnotationBindingContext bindingContext) {
		String val = getString( hints, element, bindingContext );
		if ( val == null ) {
			return null;
		}
		try {
			return Integer.decode( val );
		}
		catch ( NumberFormatException nfe ) {
			throw new AnnotationException( "Not an integer in hint: " + query + ":" + element, nfe );
		}
	}

	private static String getString(AnnotationInstance[] hints, String element, AnnotationBindingContext bindingContext) {
		final ClassLoaderService classLoaderService = bindingContext.getBuildingOptions().getServiceRegistry().getService( ClassLoaderService.class );
		for ( AnnotationInstance hint : hints ) {
			if ( element.equals( JandexHelper.getValue( hint, "name", String.class, classLoaderService ) ) ) {
				return JandexHelper.getValue( hint, "value", String.class, classLoaderService );
			}
		}
		return null;
	}

	private static Integer getTimeout(AnnotationInstance[] hints, String query, AnnotationBindingContext bindingContext) {
		Integer timeout = getInteger( hints, QueryHints.TIMEOUT_JPA, query, bindingContext );
		if ( timeout == null ) {
			return getInteger( hints, QueryHints.TIMEOUT_HIBERNATE, query, bindingContext ); // timeout is already in seconds
		}
		return ( ( timeout + 500 ) / 1000 ); // convert milliseconds to seconds (rounded)
	}
	
	private static String defaultToNull( String s ) {
		return StringHelper.isEmpty( s ) ? null : s;
	}
	
	private static Integer defaultToNull( Integer i ) {
		return i == null || i < 0 ? null : i;
	}
}
