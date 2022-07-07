package net.postchain.rell.plugin;

import net.postchain.rell.codegen.CodeGenerator;
import net.postchain.rell.codegen.document.DocumentFile;
import net.postchain.rell.codegen.document.DocumentSaver;
import net.postchain.rell.codegen.kotlin.KotlinDocumentFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

@Mojo(name = "rell-generate-client", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class RellGenMojo extends AbstractMojo {

    @Parameter(name = "source", defaultValue = "src/main/rell")
    String source;

    @Parameter(name = "target", defaultValue = "target/generated-sources")
    String target;

    @Parameter(name = "mainModule", readonly = true, required = true)
    String mainModule;

    @Parameter(name = "packageName", readonly = true, required = true)
    String packageName;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        var factory = new KotlinDocumentFactory(packageName);
        var generator = new CodeGenerator(factory, false);
        var sections = generator.createSections(new File(source), mainModule);
        var documents = generator.constructDocuments(sections, true);
        new DocumentSaver(new File(target)).saveDocuments(documents);
        getLog().info("Created files: " + documents.stream().map(DocumentFile::getPath));
    }
}
