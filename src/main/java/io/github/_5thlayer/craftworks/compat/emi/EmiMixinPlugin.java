// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Applies the EMI mixins only to the EMI they were written against, and never crashes the game over one.
 *
 * <p>The mixins are pinned to the unofficial 26.1.2 port ({@code emi-1.1.24-63e8aef+26.1.2+neoforge}).
 * Mixin itself handles a changed EMI badly for a config that isn't required: a missing method is only a
 * warning, and an injection point that no longer matches is a crash. So before a mixin is applied, every
 * method it injects into or shadows is looked up in EMI's class by name and descriptor; when one is gone
 * the mixin is skipped, an error names it, and EMI behaves as stock.
 *
 * <p>Without EMI (a JEI-only pack) nothing is applied and nothing is logged. Loaded by Mixin before the
 * game, so it names no Minecraft or EMI class.
 */
public final class EmiMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftworks");

    /** The annotations whose {@code method} names a method of the target. */
    private static final Set<String> INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;");
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (FMLLoader.getCurrent().getLoadingModList().getModFileById("emi") == null) return false;
        Set<String> missing;
        try {
            ClassNode target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            ClassNode mixin = MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName);
            missing = missing(mixin, target);
        } catch (Exception unreadable) {
            missing = Set.of("the class itself (" + unreadable + ")");
        }
        if (missing.isEmpty()) return true;
        LOGGER.error("Craftworks: EMI's {} no longer has {}, so {} is skipped and EMI behaves as stock."
                        + " Craftworks' EMI mixins are written for emi-1.1.24-63e8aef+26.1.2+neoforge.",
                targetClassName, missing, mixinClassName);
        return false;
    }

    /** The target methods the mixin names that the target doesn't have, as their selectors. */
    private static Set<String> missing(ClassNode mixin, ClassNode target) {
        Set<String> missing = new LinkedHashSet<>();
        for (MethodNode method : mixin.methods) {
            for (AnnotationNode annotation : annotations(method)) {
                if (INJECTORS.contains(annotation.desc)) {
                    for (String selector : selectors(annotation)) {
                        if (!has(target, selector)) missing.add(selector);
                    }
                } else if (SHADOW.equals(annotation.desc) && !has(target, method.name + method.desc)) {
                    missing.add(method.name + method.desc);
                }
            }
        }
        return missing;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> all = new ArrayList<>();
        if (method.visibleAnnotations != null) all.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) all.addAll(method.invisibleAnnotations);
        return all;
    }

    @SuppressWarnings("unchecked")
    private static List<String> selectors(AnnotationNode annotation) {
        if (annotation.values == null) return List.of();
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if ("method".equals(annotation.values.get(i))) return (List<String>) annotation.values.get(i + 1);
        }
        return List.of();
    }

    /** Whether the target has the method a selector names: {@code name} alone, or {@code name(desc)}. */
    private static boolean has(ClassNode target, String selector) {
        int paren = selector.indexOf('(');
        String name = paren < 0 ? selector : selector.substring(0, paren);
        String desc = paren < 0 ? null : selector.substring(paren);
        return target.methods.stream().anyMatch(m -> m.name.equals(name) && (desc == null || m.desc.equals(desc)));
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
