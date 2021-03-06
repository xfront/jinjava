package com.hubspot.jinjava.el.ext;

import java.lang.reflect.InvocationTargetException;

import javax.el.ELContext;
import javax.el.ELException;

import com.google.common.collect.ImmutableMap;
import com.hubspot.jinjava.interpret.CallStack;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.MacroTagCycleException;
import com.hubspot.jinjava.interpret.TemplateError;
import com.hubspot.jinjava.interpret.errorcategory.BasicTemplateErrorCategory;
import com.hubspot.jinjava.lib.fn.MacroFunction;
import de.odysseus.el.misc.LocalMessages;
import de.odysseus.el.tree.Bindings;
import de.odysseus.el.tree.impl.ast.AstFunction;
import de.odysseus.el.tree.impl.ast.AstParameters;

public class AstMacroFunction extends AstFunction {

  public AstMacroFunction(String name, int index, AstParameters params, boolean varargs) {
    super(name, index, params, varargs);
  }

  @Override
  public Object eval(Bindings bindings, ELContext context) {
    JinjavaInterpreter interpreter = (JinjavaInterpreter) context.getELResolver().getValue(context, null, ExtendedParser.INTERPRETER);

    MacroFunction macroFunction = interpreter.getContext().getGlobalMacro(getName());
    if (macroFunction != null) {

      CallStack macroStack = interpreter.getContext().getMacroStack();
      if (!macroFunction.isCaller()) {
        try {
          if (interpreter.getConfig().isEnableRecursiveMacroCalls()) {
            if (interpreter.getConfig().getMaxMacroRecursionDepth() != 0) {
              macroStack.pushWithMaxDepth(getName(), interpreter.getConfig().getMaxMacroRecursionDepth(), -1, -1);
            } else {
              macroStack.pushWithoutCycleCheck(getName());
            }
          } else {
            macroStack.push(getName(), -1, -1);
          }
        } catch (MacroTagCycleException e) {

          int maxDepth = interpreter.getConfig().getMaxMacroRecursionDepth();
          if (maxDepth != 0 && interpreter.getConfig().isValidationMode()) {
            // validation mode is only concerned with syntax
            return "";
          }

          String message = maxDepth == 0
              ? String.format("Cycle detected for macro '%s'", getName())
              : String.format("Max recursion limit of %d reached for macro '%s'", maxDepth, getName());

          interpreter.addError(new TemplateError(TemplateError.ErrorType.WARNING,
                                                 TemplateError.ErrorReason.EXCEPTION,
                                                 TemplateError.ErrorItem.TAG,
                                                 message,
                                                 null,
                                                 e.getLineNumber(),
                                                 e.getStartPosition(),
                                                 e,
                                                 BasicTemplateErrorCategory.CYCLE_DETECTED,
                                                 ImmutableMap.of("name", getName())));

          return "";
        }
      }

      try {
        return super.invoke(bindings, context, macroFunction, AbstractCallableMethod.EVAL_METHOD);
      } catch (IllegalAccessException e) {
        throw new ELException(LocalMessages.get("error.function.access", getName()), e);
      } catch (InvocationTargetException e) {
        throw new ELException(LocalMessages.get("error.function.invocation", getName()), e.getCause());
      } finally {
        macroStack.pop();
      }
    }

    return interpreter.getContext().isValidationMode() ? "" : super.eval(bindings, context);
  }

}
