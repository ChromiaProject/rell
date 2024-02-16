package com.chromia.rell.dokka.translator

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorVisitor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.Name

class RellDeclarationDescriptor: DeclarationDescriptor {
    override fun getName(): Name {
        TODO("Not yet implemented")
    }

    override fun getOriginal(): DeclarationDescriptor {
        TODO("Not yet implemented")
    }

    override fun getContainingDeclaration(): DeclarationDescriptor? {
        TODO("Not yet implemented")
    }

    override fun <R : Any?, D : Any?> accept(p0: DeclarationDescriptorVisitor<R, D>?, p1: D): R {
        TODO("Not yet implemented")
    }

    override fun acceptVoid(p0: DeclarationDescriptorVisitor<Void, Void>?) {
        TODO("Not yet implemented")
    }

    override val annotations: Annotations
        get() = TODO("Not yet implemented")
}