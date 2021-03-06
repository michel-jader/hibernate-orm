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
package org.hibernate.metamodel.spi.binding;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.engine.spi.CascadeStyle;
import org.hibernate.metamodel.source.spi.MetaAttributeContext;
import org.hibernate.metamodel.spi.domain.Aggregate;
import org.hibernate.metamodel.spi.domain.SingularAttribute;

/**
 * Describes plural attributes of {@link org.hibernate.metamodel.spi.binding.PluralAttributeElementBinding.Nature#AGGREGATE} elements
 *
 * @author Steve Ebersole
 * @author Gail Badner
 */
public class CompositePluralAttributeElementBinding
		extends AbstractPluralAttributeElementBinding
		implements Cascadeable {

	// TODO: Come up with a more descriptive name for compositeAttributeBindingContainer.
	private AbstractCompositeAttributeBindingContainer compositeAttributeBindingContainer;
	private CascadeStyle cascadeStyle;



	public CompositePluralAttributeElementBinding(AbstractPluralAttributeBinding binding) {
		super( binding );
	}

	@Override
	protected RelationalValueBindingContainer getRelationalValueContainer() {
		return compositeAttributeBindingContainer.getRelationalValueBindingContainer();
	}

	@Override
	public Nature getNature() {
		return Nature.AGGREGATE;
	}

	public CompositeAttributeBindingContainer createCompositeAttributeBindingContainer(
			Aggregate aggregate,
			MetaAttributeContext metaAttributeContext,
			SingularAttribute parentReference) {
		compositeAttributeBindingContainer =
				new AbstractCompositeAttributeBindingContainer(
						getPluralAttributeBinding().getContainer().seekEntityBinding(),
						aggregate,
						getPluralAttributeBinding().getPluralAttributeKeyBinding().getCollectionTable(),
						getPluralAttributeBinding().getAttribute().getRole() + ".element",
						metaAttributeContext,
						parentReference) {
					final Map<String,AttributeBinding> attributeBindingMap = new LinkedHashMap<String, AttributeBinding>();

					@Override
					protected boolean isModifiable() {
						return true;
					}

					@Override
					protected Map<String, AttributeBinding> attributeBindingMapInternal() {
						return attributeBindingMap;
					}

					@Override
					public boolean isAggregated() {
						return true;
					}
				};
		return compositeAttributeBindingContainer;
	}

	public CompositeAttributeBindingContainer getCompositeAttributeBindingContainer() {
		return compositeAttributeBindingContainer;
	}

	@Override
	public CascadeStyle getCascadeStyle() {
		return cascadeStyle;
	}

	@Override
	public void setCascadeStyle(CascadeStyle cascadeStyle) {
		this.cascadeStyle = cascadeStyle;
	}
}
