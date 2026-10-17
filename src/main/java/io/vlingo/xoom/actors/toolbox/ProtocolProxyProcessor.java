// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import io.vlingo.xoom.actors.SafeProxyGenerable;

/**
 * Generates the {@code <Protocol>__Proxy} class of every {@link ActorProtocol} at build time.
 *
 * <p>This is all the runtime needs: {@code ActorProxy.createFor} first looks the proxy class up by
 * name and only generates and compiles one with javac when it is not found. A generated proxy
 * therefore removes the runtime compilation, keeps builds reproducible, and is what makes
 * native-image possible.
 *
 * <p>The generated class is the exact contract the runtime looks for: a public class named
 * {@code <Protocol>__Proxy} in the protocol's package, extending {@code ActorProxyBase} and
 * implementing the protocol and {@code Proxy}, with a constructor taking {@code (Actor, Mailbox)}.
 */
public final class ProtocolProxyProcessor extends AbstractProcessor {

  private static final String PROXY_SUFFIX = "__Proxy";
  private static final String VOID = "void";
  private static final String OBJECT = "java.lang.Object";
  private static final String ACTOR = "io.vlingo.xoom.actors.Actor";
  private static final String ACTOR_PROXY_BASE = "io.vlingo.xoom.actors.ActorProxyBase";
  private static final String ADDRESS = "io.vlingo.xoom.actors.Address";
  private static final String DEAD_LETTER = "io.vlingo.xoom.actors.DeadLetter";
  private static final String DEFINITION = "io.vlingo.xoom.actors.Definition";
  private static final String LOCAL_MESSAGE = "io.vlingo.xoom.actors.LocalMessage";
  private static final String MAILBOX = "io.vlingo.xoom.actors.Mailbox";
  private static final String PROXY = "io.vlingo.xoom.actors.Proxy";
  private static final String RETURNS = "io.vlingo.xoom.actors.Returns";
  private static final String COMPLETES = "io.vlingo.xoom.common.Completes";
  private static final String SERIALIZABLE_CONSUMER = "io.vlingo.xoom.common.SerializableConsumer";
  private static final String FUTURE = "java.util.concurrent.Future";
  private static final String COMPLETABLE_FUTURE = "java.util.concurrent.CompletableFuture";
  private static final String COMPLETABLE_FUTURE_NEW = "new java.util.concurrent.CompletableFuture<>()";
  private static final String GENERATED = "@javax.annotation.processing.Generated(\"io.vlingo.xoom.actors.toolbox.ProtocolProxyProcessor\")";
  private static final String NATIVE_IMAGE_METADATA = "META-INF/native-image/reflect-config.json";

  private final Set<String> proxies = new LinkedHashSet<>();
  private Writer nativeImageMetadata;
  private int nativeImageEntries;

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Set.of(ActorProtocol.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment round) {
    protocolsOf(round).forEach(this::generateProxy);
    if (round.processingOver()) {
      closeNativeImageMetadata();
    }
    return true;
  }

  private Set<TypeElement> protocolsOf(final RoundEnvironment round) {
    final TypeElement annotation = elements().getTypeElement(ActorProtocol.class.getName());
    return annotation == null
        ? Set.of()
        : ElementFilter.typesIn(round.getElementsAnnotatedWith(annotation));
  }

  private void generateProxy(final TypeElement protocol) {
    final List<String> problems = problemsWith(protocol);
    if (!problems.isEmpty()) {
      problems.forEach(problem -> error(protocol, problem));
      return;
    }

    final String proxyName = proxyNameOf(protocol);
    writeSource(proxyName, proxySource(protocol));
    proxies.add(proxyName);
    writeNativeImageMetadataEntry(proxyName);
  }

  private List<String> problemsWith(final TypeElement protocol) {
    if (protocol.getKind() != ElementKind.INTERFACE) {
      return List.of("@" + ActorProtocol.class.getSimpleName() + " requires an interface, but "
          + protocol.getQualifiedName() + " is not an interface.");
    }
    if (isSafeProxyGenerable(protocol.asType())) {
      return List.of();
    }
    return protocolMethods(protocol).stream()
        .filter(method -> !isProxyableReturnType(method.getReturnType().toString()))
        .map(this::returnTypeProblemWith)
        .collect(Collectors.toList());
  }

  private String returnTypeProblemWith(final ExecutableElement method) {
    return "Protocol method " + signatureOf(method) + " returns " + method.getReturnType() + ". A protocol"
        + " method must return " + VOID + ", " + COMPLETES + "<T>, " + FUTURE + "<T> or "
        + COMPLETABLE_FUTURE + "<T>, or the protocol (or one of its super interfaces) must be annotated with @"
        + SafeProxyGenerable.class.getName() + ".";
  }

  private String proxySource(final TypeElement protocol) {
    final String protocolType = typeUseOf(protocol);
    final String rawProtocol = protocol.getQualifiedName().toString();
    final List<ExecutableElement> methods = protocolMethods(protocol);
    final StringBuilder source = new StringBuilder();

    source.append("package ").append(packageOf(protocol)).append(";\n\n")
        .append(GENERATED).append("\n")
        .append("public class ").append(proxySimpleNameOf(protocol)).append(typeParametersOf(protocol))
        .append(" extends ").append(ACTOR_PROXY_BASE).append("<").append(protocolType).append(">")
        .append(" implements ").append(protocolType).append(", ").append(PROXY)
        .append(" {\n");

    appendRepresentations(source, methods);
    appendInstanceVariables(source);
    appendConstructors(source, proxySimpleNameOf(protocol), protocolType, rawProtocol);
    appendObjectMethods(source, protocol);
    for (int index = 0; index < methods.size(); index++) {
      appendMethod(source, methods.get(index), protocolType, rawProtocol, index + 1);
    }

    return source.append("}\n").toString();
  }

  private void appendRepresentations(final StringBuilder source, final List<ExecutableElement> methods) {
    for (int index = 0; index < methods.size(); index++) {
      final ExecutableElement method = methods.get(index);
      source.append("\n  private static final String ").append(representationNameOf(method, index + 1))
          .append(" = \"").append(method.getSimpleName()).append("(").append(parameterTypesOf(method)).append(")\";\n");
    }
  }

  private void appendInstanceVariables(final StringBuilder source) {
    source.append("\n  private final ").append(ACTOR).append(" actor;\n")
        .append("  private final ").append(MAILBOX).append(" mailbox;\n");
  }

  private void appendConstructors(
          final StringBuilder source,
          final String proxySimpleName,
          final String protocolType,
          final String rawProtocol) {
    source.append("\n  public ").append(proxySimpleName)
        .append("(final ").append(ACTOR).append(" actor, final ").append(MAILBOX).append(" mailbox) {\n")
        .append("    super(").append(rawProtocol).append(".class, ")
        .append(DEFINITION).append(".SerializationProxy.from(actor.definition()), actor.address());\n")
        .append("    this.actor = actor;\n")
        .append("    this.mailbox = mailbox;\n")
        .append("  }\n")
        .append("\n  public ").append(proxySimpleName).append("() {\n")
        .append("    super();\n")
        .append("    this.actor = null;\n")
        .append("    this.mailbox = null;\n")
        .append("  }\n");
  }

  private void appendObjectMethods(final StringBuilder source, final TypeElement protocol) {
    source.append("\n  @Override\n")
        .append("  public ").append(ADDRESS).append(" address() {\n")
        .append("    return this.actor.address();\n")
        .append("  }\n")
        .append("\n  @Override\n")
        .append("  public boolean equals(final Object other) {\n")
        .append("    if (this == other) return true;\n")
        .append("    if (other == null) return false;\n")
        .append("    if (other.getClass() != getClass()) return false;\n")
        .append("    return this.actor.address().equals(").append(PROXY).append(".from(other).address());\n")
        .append("  }\n")
        .append("\n  @Override\n")
        .append("  public int hashCode() {\n")
        .append("    return 31 + getClass().hashCode() + this.actor.address().hashCode();\n")
        .append("  }\n")
        .append("\n  @Override\n")
        .append("  public String toString() {\n")
        .append("    return \"").append(protocol.getQualifiedName()).append("[address=\" + this.actor.address() + \"]\";\n")
        .append("  }\n");
  }

  private void appendMethod(
          final StringBuilder source,
          final ExecutableElement method,
          final String protocolType,
          final String rawProtocol,
          final int index) {

    final String returnType = method.getReturnType().toString();
    final String representation = representationNameOf(method, index);
    final boolean answers = answersWith(returnType);

    source.append("\n  @Override\n")
        .append("  public ").append(typeParametersWithSpaceOf(method)).append(returnType).append(" ")
        .append(method.getSimpleName()).append("(").append(parametersOf(method)).append(") {\n")
        .append("    if (!this.actor.isStopped()) {\n");

    if (!method.getParameters().isEmpty()) {
      source.append("      ").append(ACTOR_PROXY_BASE).append("<").append(protocolType).append("> self = this;\n");
    }

    source.append("      final ").append(SERIALIZABLE_CONSUMER).append("<").append(protocolType)
        .append("> consumer = (protocol) -> protocol.").append(method.getSimpleName()).append(argumentsOf(method)).append(";\n");

    appendReturnValue(source, returnType);
    appendSend(source, rawProtocol, protocolType, representation, answers);

    if (answers) {
      source.append("      return returnValue;\n");
    }

    source.append("    } else {\n")
        .append("      this.actor.deadLetters().failedDelivery(new ").append(DEAD_LETTER)
        .append("(this.actor, ").append(representation).append("));\n")
        .append("    }\n");

    appendAbsentReturn(source, returnType);
    source.append("  }\n");
  }

  private void appendReturnValue(final StringBuilder source, final String returnType) {
    if (isCompletes(returnType)) {
      source.append("      final ").append(returnType).append(" returnValue = ")
          .append(COMPLETES).append(".using(this.actor.scheduler());\n");
    } else if (isFuture(returnType)) {
      source.append("      final ").append(returnType).append(" returnValue = ")
          .append(COMPLETABLE_FUTURE_NEW).append(";\n");
    }
  }

  private void appendSend(
          final StringBuilder source,
          final String rawProtocol,
          final String protocolType,
          final String representation,
          final boolean answers) {

    final String returns = answers ? RETURNS + ".value(returnValue), " : "";
    source.append("      if (this.mailbox.isPreallocated()) { this.mailbox.send(this.actor, ")
        .append(rawProtocol).append(".class, consumer, ").append(answers ? returns : "null, ").append(representation).append("); }\n")
        .append("      else { this.mailbox.send(new ").append(LOCAL_MESSAGE).append("<").append(protocolType)
        .append(">(this.actor, ").append(rawProtocol).append(".class, consumer, ").append(returns).append(representation).append(")); }\n");
  }

  private void appendAbsentReturn(final StringBuilder source, final String returnType) {
    final String absent = absentValueOf(returnType);
    if (!absent.isEmpty()) {
      source.append("    return ").append(absent).append(";\n");
    }
  }

  private String absentValueOf(final String returnType) {
    switch (returnType) {
      case VOID:
        return "";
      case "boolean":
        return "false";
      case "char":
        return "'\\0'";
      case "byte":
      case "short":
      case "int":
      case "long":
      case "float":
      case "double":
        return "0";
      default:
        return "null";
    }
  }

  private List<ExecutableElement> protocolMethods(final TypeElement protocol) {
    final Map<String, ExecutableElement> mostDerived = new LinkedHashMap<>();
    for (final ExecutableElement method : ElementFilter.methodsIn(elements().getAllMembers(protocol))) {
      if (isProxyMethod(method)) {
        mostDerived.merge(signatureOf(method), method, this::mostDerivedOf);
      }
    }
    return List.copyOf(mostDerived.values());
  }

  private boolean isProxyMethod(final ExecutableElement method) {
    return !method.getModifiers().contains(Modifier.STATIC)
        && method.getEnclosingElement().getKind() == ElementKind.INTERFACE;
  }

  private ExecutableElement mostDerivedOf(final ExecutableElement left, final ExecutableElement right) {
    return types().isSubtype(right.getEnclosingElement().asType(), left.getEnclosingElement().asType())
        ? right
        : left;
  }

  private String signatureOf(final ExecutableElement method) {
    return method.getSimpleName() + method.getParameters().stream()
        .map(parameter -> types().erasure(parameter.asType()).toString())
        .collect(Collectors.joining(", ", "(", ")"));
  }

  private String representationNameOf(final ExecutableElement method, final int index) {
    return method.getSimpleName() + "Representation" + index;
  }

  private String parametersOf(final ExecutableElement method) {
    return method.getParameters().stream()
        .map(parameter -> "final " + parameter.asType() + " " + parameter.getSimpleName())
        .collect(Collectors.joining(", "));
  }

  private String parameterTypesOf(final ExecutableElement method) {
    return method.getParameters().stream()
        .map(parameter -> parameter.asType().toString())
        .collect(Collectors.joining(", "));
  }

  private String argumentsOf(final ExecutableElement method) {
    return method.getParameters().stream()
        .map(parameter -> ACTOR_PROXY_BASE + ".thunk(self, this.actor, " + parameter.getSimpleName() + ")")
        .collect(Collectors.joining(", ", "(", ")"));
  }

  private String typeParametersOf(final Parameterizable element) {
    final List<? extends TypeParameterElement> parameters = element.getTypeParameters();
    return parameters.isEmpty()
        ? ""
        : parameters.stream().map(this::parameterDeclarationOf).collect(Collectors.joining(", ", "<", ">"));
  }

  private String typeParametersWithSpaceOf(final ExecutableElement method) {
    final String parameters = typeParametersOf(method);
    return parameters.isEmpty() ? "" : parameters + " ";
  }

  private String parameterDeclarationOf(final TypeParameterElement parameter) {
    final String bounds = parameter.getBounds().stream()
        .map(TypeMirror::toString)
        .filter(bound -> !bound.equals(OBJECT))
        .collect(Collectors.joining(" & "));
    return bounds.isEmpty()
        ? parameter.getSimpleName().toString()
        : parameter.getSimpleName() + " extends " + bounds;
  }

  private String typeUseOf(final TypeElement protocol) {
    final List<? extends TypeParameterElement> parameters = protocol.getTypeParameters();
    return parameters.isEmpty()
        ? protocol.getQualifiedName().toString()
        : protocol.getQualifiedName() + parameters.stream()
            .map(parameter -> parameter.getSimpleName().toString())
            .collect(Collectors.joining(", ", "<", ">"));
  }

  private String packageOf(final TypeElement protocol) {
    return elements().getPackageOf(protocol).getQualifiedName().toString();
  }

  private String proxyNameOf(final TypeElement protocol) {
    return packageOf(protocol) + "." + proxySimpleNameOf(protocol);
  }

  private String proxySimpleNameOf(final TypeElement protocol) {
    return flatNameOf(protocol) + PROXY_SUFFIX;
  }

  /**
   * Answers the name the runtime composes, which concatenates the simple names of all enclosing
   * types before the protocol's own simple name.
   */
  private String flatNameOf(final TypeElement type) {
    final Element enclosing = type.getEnclosingElement();
    return enclosing.getKind() == ElementKind.PACKAGE
        ? type.getSimpleName().toString()
        : flatNameOf((TypeElement) enclosing) + type.getSimpleName();
  }

  private boolean isProxyableReturnType(final String returnType) {
    return returnType.equals(VOID) || isCompletes(returnType) || isFuture(returnType);
  }

  private boolean answersWith(final String returnType) {
    return isCompletes(returnType) || isFuture(returnType);
  }

  private boolean isCompletes(final String returnType) {
    return returnType.startsWith(COMPLETES);
  }

  private boolean isFuture(final String returnType) {
    return returnType.startsWith(FUTURE) || returnType.startsWith(COMPLETABLE_FUTURE);
  }

  private boolean isSafeProxyGenerable(final TypeMirror type) {
    final Element element = types().asElement(type);
    if (!(element instanceof TypeElement)) {
      return false;
    }
    return element.getAnnotation(SafeProxyGenerable.class) != null
        || types().directSupertypes(type).stream().anyMatch(this::isSafeProxyGenerable);
  }

  private void writeSource(final String proxyName, final String source) {
    try {
      final JavaFileObject file = processingEnv.getFiler().createSourceFile(proxyName);
      try (final Writer writer = file.openWriter()) {
        writer.write(source);
      }
    } catch (final IOException e) {
      error(null, "Cannot write " + proxyName + ": " + e.getMessage());
    }
  }

  private void writeNativeImageMetadataEntry(final String proxyName) {
    try {
      if (nativeImageMetadata == null) {
        nativeImageMetadata = processingEnv.getFiler()
            .createResource(StandardLocation.CLASS_OUTPUT, "", NATIVE_IMAGE_METADATA)
            .openWriter();
        nativeImageMetadata.write("[\n");
      }
      if (nativeImageEntries++ > 0) {
        nativeImageMetadata.write(",\n");
      }
      nativeImageMetadata.write("  {\n    \"name\": \"" + proxyName + "\",\n    \"allDeclaredConstructors\": true\n  }");
      nativeImageMetadata.flush();
    } catch (final IOException e) {
      error(null, "Cannot write " + NATIVE_IMAGE_METADATA + ": " + e.getMessage());
    }
  }

  private void closeNativeImageMetadata() {
    if (nativeImageMetadata == null) {
      return;
    }
    try {
      nativeImageMetadata.write("\n]\n");
      nativeImageMetadata.close();
    } catch (final IOException e) {
      error(null, "Cannot write " + NATIVE_IMAGE_METADATA + ": " + e.getMessage());
    }
  }

  private Elements elements() {
    return processingEnv.getElementUtils();
  }

  private Types types() {
    return processingEnv.getTypeUtils();
  }

  private void error(final Element element, final String message) {
    if (element == null) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
    } else {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
  }
}
