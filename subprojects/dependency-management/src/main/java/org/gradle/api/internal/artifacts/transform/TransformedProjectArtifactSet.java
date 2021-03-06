/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;

import java.util.Collection;

/**
 * An artifact set containing transformed project artifacts.
 */
public class TransformedProjectArtifactSet extends AbstractTransformedArtifactSet implements ConsumerProvidedVariantFiles {
    private final ComponentIdentifier componentIdentifier;
    private final ResolvedArtifactSet delegate;
    private final AttributeContainerInternal attributes;
    private final Transformation transformation;
    private final TransformationNodeRegistry transformationNodeRegistry;

    public TransformedProjectArtifactSet(
        ComponentIdentifier componentIdentifier,
        ResolvedArtifactSet delegate,
        ImmutableAttributes target,
        Transformation transformation,
        ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory,
        TransformationNodeRegistry transformationNodeRegistry
    ) {
        super(componentIdentifier, delegate, target, transformation, dependenciesResolverFactory, transformationNodeRegistry);
        this.componentIdentifier = componentIdentifier;
        this.delegate = delegate;
        this.attributes = target;
        this.transformation = transformation;
        this.transformationNodeRegistry = transformationNodeRegistry;
    }

    @Override
    public ImmutableAttributes getTargetVariantAttributes() {
        return attributes.asImmutable();
    }

    @Override
    public DisplayName getTargetVariantName() {
        return Describables.of(componentIdentifier, attributes);
    }

    @Override
    public String toString() {
        return getTargetVariantName().getCapitalizedDisplayName();
    }

    @Override
    public Transformation getTransformation() {
        return transformation;
    }

    @Override
    public Object getSource() {
        return delegate;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        Collection<TransformationNode> scheduledNodes = transformationNodeRegistry.getOrCreate(delegate, transformation, getDependenciesResolver());
        if (!scheduledNodes.isEmpty()) {
            context.add(new DefaultTransformationDependency(scheduledNodes));
        }
    }

    @Override
    public Collection<TransformationNode> getScheduledNodes() {
        return transformationNodeRegistry.getOrCreate(delegate, transformation, getDependenciesResolver());
    }

    @Override
    protected FileCollectionInternal.Source getVisitSource() {
        return this;
    }
}
