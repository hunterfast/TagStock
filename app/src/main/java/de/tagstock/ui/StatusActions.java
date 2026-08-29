package de.tagstock.ui;

import android.content.Context;
import android.widget.Toast;

import de.tagstock.R;
import de.tagstock.data.Item;
import de.tagstock.data.ItemStatus;
import de.tagstock.data.Repository;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Formatter;

/** Statuswechsel eines Artikels inklusive Rueckfrage nach dem Ausleiher. */
public final class StatusActions {

    private StatusActions() {
    }

    public static void change(Context context, Repository repository, Item item, ItemStatus target) {
        if (target == ItemStatus.VERLIEHEN) {
            Dialogs.textInput(context,
                    context.getString(R.string.artikel_verliehen_an),
                    context.getString(R.string.artikel_verliehen_an),
                    item.verliehenAn,
                    name -> {
                        Item updated = item.copy();
                        updated.status = ItemStatus.VERLIEHEN;
                        updated.verliehenAn = name;
                        if (updated.verliehenSeit == null) {
                            updated.verliehenSeit = System.currentTimeMillis();
                        }
                        save(context, repository, updated);
                    });
            return;
        }

        Item updated = item.copy();
        updated.status = target;
        updated.verliehenAn = null;
        updated.verliehenSeit = null;
        save(context, repository, updated);
    }

    private static void save(Context context, Repository repository, Item item) {
        repository.updateItem(item);
        Toast.makeText(context,
                context.getString(R.string.status_geaendert, Formatter.statusLabel(context, item.status)),
                Toast.LENGTH_SHORT).show();
    }
}
