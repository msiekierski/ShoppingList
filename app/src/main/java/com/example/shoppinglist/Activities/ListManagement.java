package com.example.shoppinglist.Activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.backendless.Backendless;
import com.backendless.BackendlessUser;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.exceptions.BackendlessFault;
import com.backendless.persistence.DataQueryBuilder;
import com.example.shoppinglist.ApplicationClass;
import com.example.shoppinglist.Models.SharedLists;
import com.example.shoppinglist.RecyclerViewAdapters.ListRecyclerViewAdapter;
import com.example.shoppinglist.Models.Products;
import com.example.shoppinglist.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ListManagement extends AppCompatActivity{
    private final static String LIST = "Lista: ";
    private final static String LIST_SAVE = "Przypis listy do konta";
    private final static String ENTER_LIST_NAME = "Wprowadź nazwę dla swojej listy";
    private final static String CANCEL = "Anuluj";
    private final static String SAVING_LIST = "Trwa zapisywanie listy...proszę czekać...";
    private final static String PICK_LIST = "Wybierz listę do wczytania";
    private final static String LIST_LOADING = "Wczytuję listę...proczę czekać...";
    private final static String ERROR = "Błąd: ";
    private final static String LOAD_LIST = "Załaduj listę";
    private final static String DOWNLOADING_LIST_NAMES = "Pobieranie nazw...proszę czekać...";
    private final static String DOWNLOADING_SHARED_LIST_NAMES = "Pobieranie dzielonych list...";
    private final static String FILL_ALL_FIELDS = "Wypełnij wszystkie pola produktów!";
    private final static String NO_LISTS = "Brak list do wczytania.";
    private final static String CANNOT_SAVE_EMPTY_LIST = "Nie można zapisać pustej listy.";
    private final static String LIST_SAVED = "Lista pomyślnie zapisana.";
    private final static String _NAME = "name";

    private View progressView, loginFormView;
    private TextView tvLoad;
    private RecyclerView rvProducts;
    private RecyclerView.Adapter rvAdapter;
    private Set <String> listNames;
    private Set <SharedLists> sharedLists = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_management);

        findViews();
        setUpRecyclerView();
    }

    private void setUpRecyclerView() {
        RecyclerView.LayoutManager rvLayoutManager = new LinearLayoutManager(this);
        rvProducts.setHasFixedSize(true);
        rvProducts.setLayoutManager(rvLayoutManager);
        rvAdapter = new ListRecyclerViewAdapter(this, ApplicationClass.lastManagedList);
        rvProducts.setAdapter(rvAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.activity_management_menu, menu);
        getSupportActionBar().setTitle(LIST + ApplicationClass.lastManagedListName);
        return true;
    }



   public void ivAddListener(View view) {
        Products products = new Products();
        products.setBeingEdited(true);
        ApplicationClass.lastManagedList.add(products);
        rvAdapter.notifyDataSetChanged();
   }



    private void findViews() {
        progressView = findViewById(R.id.login_progress);
        loginFormView = findViewById(R.id.login_form);
        tvLoad = findViewById(R.id.tvLoad);
        rvProducts = findViewById(R.id.rvProducts);
    }

    private void showProgress(final boolean show) {
        progressView.setVisibility(show ? View.VISIBLE : View.GONE);
        tvLoad.setVisibility(show ? View.VISIBLE : View.GONE);
        loginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.saveListItem:
                saveList();
                break;
            case R.id.loadListItem:
                listNames = getListNames();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean checkIfAllFieldsAreFilled() {
        for (Products product: ApplicationClass.lastManagedList) {
            if (product.getProductName() == null || product.getQuantity() == null || product.getUnit() == null) {
                return false;
            }
        }
        return true;
    }

    private void saveList () {
        if (ApplicationClass.lastManagedList.size() == 0) {
            showToast(CANNOT_SAVE_EMPTY_LIST);
            return;
        }
        if (!checkIfAllFieldsAreFilled()) {
            showToast(FILL_ALL_FIELDS);
            return;
        }

        for (Products product: ApplicationClass.lastManagedList)
            product.setBeingEdited(false);
        rvAdapter.notifyDataSetChanged();

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(LIST_SAVE)
                .setMessage(ENTER_LIST_NAME)
                .setView(input)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ApplicationClass.lastManagedListName = input.getText().toString().trim();
                        supportInvalidateOptionsMenu();
                        updateListNameInProductList();
                        saveListOnServer();
                    }
                })
                .setNegativeButton(CANCEL, null)
                .show();
    }

    private void saveListOnServer () {
        showProgress(true);
        tvLoad.setText(SAVING_LIST);
        for (Products product: ApplicationClass.lastManagedList) {
            Backendless.Data.of(Products.class).save(product, new AsyncCallback<Products>() {
                @Override
                public void handleResponse(Products response){
                    showToast(LIST_SAVED);
                }
                @Override
                public void handleFault(BackendlessFault fault) {
                    Toast.makeText(ListManagement.this, ERROR + fault.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
        showProgress(false);
    }

    private void updateListNameInProductList() {
        for (Products product: ApplicationClass.lastManagedList) {
            product.setListName(ApplicationClass.lastManagedListName);
        }
    }

    private LinkedHashSet getListNames () {
        final LinkedHashSet names = new LinkedHashSet();

        String whereClause = "ownerId = '" + ApplicationClass.user.getUserId() + "'";
        DataQueryBuilder dataQueryBuilder = DataQueryBuilder.create();
        dataQueryBuilder.setWhereClause(whereClause);
        showProgress(true);
        tvLoad.setText(DOWNLOADING_LIST_NAMES);

        Backendless.Persistence.of(Products.class).find(dataQueryBuilder, new AsyncCallback<List<Products>>() {
            @Override
            public void handleResponse(List<Products> response) {
                for (Products product: response) {
                    names.add(product.getListName());
                }
                findSharedLists();
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(ListManagement.this, ERROR + fault.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        return names;
    }

    private void findSharedLists() {
        String whereClause = "friendId = '" + ApplicationClass.user.getUserId() + "'";
        DataQueryBuilder dataQueryBuilder = DataQueryBuilder.create();
        dataQueryBuilder.setWhereClause(whereClause);

        tvLoad.setText(DOWNLOADING_SHARED_LIST_NAMES);
        Backendless.Persistence.of(SharedLists.class).find(dataQueryBuilder, new AsyncCallback<List<SharedLists>>() {
            @Override
            public void handleResponse(List<SharedLists> response) {
                for (SharedLists sharedList: response) {
                    listNames.add(sharedList.getListName());
                }
                sharedLists.addAll(response);
                runAlertList();
                showProgress(false);
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                showProgress(false);
                showToast("ERROR: " + fault.getMessage());

            }
        });
    }

    private void runAlertList () {
        final int[] checkedItem = {0};
        final String [] arrayListNames = setToArray(listNames);
        if (arrayListNames.length == 0) {
            showToast(NO_LISTS);
            return;
        }


         new AlertDialog.Builder(this)
                .setTitle(PICK_LIST)
                .setSingleChoiceItems(arrayListNames, checkedItem[0], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        checkedItem[0] = i;
                    }
                })
                .setNegativeButton(CANCEL, null)
                .setPositiveButton(LOAD_LIST, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ApplicationClass.lastManagedListName = arrayListNames[checkedItem[0]];
                        loadListFromServer(arrayListNames[checkedItem[0]]);
                    }
                })
                .show();
    }

    private String [] setToArray (Set <String> set) {
        String [] arr = set.toArray(new String[set.size()]);
        return arr;
    }




    private void loadListFromServer (String listName) {
        String whereClause = "(ownerId = '" + ApplicationClass.user.getUserId() +
                "' AND listName = '" + listName + "')";
        ArrayList <SharedLists> shared = new ArrayList<>(sharedLists);
        for (int i = 0; i < shared.size(); i++) {
            if (listName.equals(shared.get(i).getListName()) && shared.get(i).getFriendId().equals(ApplicationClass.user.getUserId())) {
                whereClause += " OR (ownerId = '" + shared.get(i).getOwnerId() + "' AND listName = '" + shared.get(i).getListName() + "')";
                break;
            }
        }
        DataQueryBuilder dataQueryBuilder = DataQueryBuilder.create();
        dataQueryBuilder.setWhereClause(whereClause);
        showProgress(true);
        tvLoad.setText(LIST_LOADING);

        Backendless.Persistence.of(Products.class).find(dataQueryBuilder, new AsyncCallback<List<Products>>() {
            @Override
            public void handleResponse(List<Products> response) {
                ApplicationClass.lastManagedList.clear();
                ApplicationClass.lastManagedList.addAll(response);
                rvAdapter.notifyDataSetChanged();
                supportInvalidateOptionsMenu();
                showProgress(false);
            }

            @Override
            public void handleFault(BackendlessFault fault) {
                Toast.makeText(ListManagement.this, ERROR + fault.getMessage(), Toast.LENGTH_LONG).show();
                showProgress(false);
            }
        });
    }

    private void showToast (String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

}
