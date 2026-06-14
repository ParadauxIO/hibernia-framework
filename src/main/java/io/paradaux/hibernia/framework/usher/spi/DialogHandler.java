package io.paradaux.hibernia.framework.usher.spi;

/**
 * Marker interface for a dialog handler — the dialog-tier analogue of
 * {@link io.paradaux.hibernia.framework.commander.spi.CommandHandler}.
 *
 * <p>An implementation is annotated {@link io.paradaux.hibernia.framework.usher.annotations.Dialog @Dialog}
 * and declares one or more {@link io.paradaux.hibernia.framework.usher.annotations.Screen @Screen}
 * methods (each returns a {@link io.paradaux.hibernia.framework.usher.DialogView}) and
 * {@link io.paradaux.hibernia.framework.usher.annotations.Action @Action} methods (invoked when a
 * button is clicked). All the screens of one handler form a single navigable flow that shares a
 * {@link io.paradaux.hibernia.framework.usher.annotations.Model model} object.</p>
 *
 * <p>Handlers are constructed by Guice and bound into a {@code Set<DialogHandler>} multibinder
 * (e.g. via {@code HiberniaModule.forPlugin(...).dialogs(...)}), so they take their services through
 * constructor injection — keeping the dialog → service → persistence layering intact.</p>
 */
public interface DialogHandler {
}
