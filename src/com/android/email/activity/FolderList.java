package com.android.email.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import com.android.email.K9ListActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Config;
import android.util.Log;
import android.text.format.DateFormat;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.email.Account;
import com.android.email.Email;
import com.android.email.MessagingController;
import com.android.email.MessagingListener;
import com.android.email.Preferences;
import com.android.email.R;
import com.android.email.activity.setup.AccountSettings;
import com.android.email.activity.setup.FolderSettings;
import com.android.email.mail.Folder;
import com.android.email.mail.Message;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Store;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

/**
 * FolderList is the primary user interface for the program. This
 * Activity shows list of the Account's folders
 */

public class FolderList extends K9ListActivity
{

    private static final int DIALOG_MARK_ALL_AS_READ = 1;

    private static final String EXTRA_ACCOUNT = "account";

    private static final String EXTRA_INITIAL_FOLDER = "initialFolder";

    private static final String STATE_CURRENT_FOLDER = "com.android.email.activity.folderlist_folder";

    private static final String EXTRA_CLEAR_NOTIFICATION = "clearNotification";

    private static final String EXTRA_STARTUP = "startup";

    private static final boolean REFRESH_REMOTE = true;

    private ListView mListView;

    private FolderListAdapter mAdapter;

    private LayoutInflater mInflater;

    private Account mAccount;

    private FolderListHandler mHandler = new FolderListHandler();

    private boolean mStartup = false;

    private java.text.DateFormat mDateFormat;

    private java.text.DateFormat mTimeFormat;


    class FolderListHandler extends Handler
    {

        private static final int MSG_PROGRESS = 2;
        private static final int MSG_DATA_CHANGED = 3;
        private static final int MSG_FOLDER_LOADING = 7;
        private static final int MSG_FOLDER_SYNCING = 18;
        private static final int MSG_SENDING_OUTBOX = 19;
        private static final int MSG_ACCOUNT_SIZE_CHANGED = 20;
        private static final int MSG_WORKING_ACCOUNT = 21;
        private static final int MSG_NEW_FOLDERS = 22;

        @Override
        public void handleMessage(android.os.Message msg)
        {
            switch (msg.what)
            {
                case MSG_NEW_FOLDERS:
                    ArrayList<FolderInfoHolder> newFolders = (ArrayList<FolderInfoHolder>)msg.obj;
                    mAdapter.mFolders.clear();

                    mAdapter.mFolders.addAll(newFolders);

                    mHandler.dataChanged();
                    break;
                case MSG_PROGRESS:
                    setProgressBarIndeterminateVisibility(msg.arg1 != 0);
                    break;
                case MSG_DATA_CHANGED:
                    mAdapter.notifyDataSetChanged();
                    break;
                case MSG_FOLDER_LOADING:
                {
                    FolderInfoHolder folder = mAdapter.getFolder((String) msg.obj);

                    if (folder != null)
                    {
                        folder.loading = msg.arg1 != 0;
                    }

                    break;
                }

                case MSG_ACCOUNT_SIZE_CHANGED:
                {
                    Long[] sizes = (Long[])msg.obj;
                    String toastText = getString(R.string.account_size_changed, mAccount.getDescription(), SizeFormatter.formatSize(getApplication(), sizes[0]), SizeFormatter.formatSize(getApplication(), sizes[1]));

                    Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_LONG);
                    toast.show();
                    break;
                }

                case MSG_WORKING_ACCOUNT:
                {
                    int res = msg.arg1;
                    String toastText = getString(res, mAccount.getDescription());

                    Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_SHORT);
                    toast.show();
                    break;
                }


                case MSG_FOLDER_SYNCING:
                {
                    String folderName = (String)((Object[]) msg.obj)[0];
                    String dispString;
                    dispString = mAccount.getDescription();

                    if (folderName != null)
                    {
                        dispString += " (" + getString(R.string.status_loading) + folderName + ")";
                    }

                    setTitle(dispString);

                    break;
                }

                case MSG_SENDING_OUTBOX:
                {
                    boolean sending = (msg.arg1 != 0);
                    String dispString;
                    dispString = mAccount.getDescription();

                    if (sending)
                    {
                        dispString += " (" + getString(R.string.status_sending) + ")";
                    }

                    setTitle(dispString);

                    break;
                }

                default:
                    super.handleMessage(msg);
            }
        }

        public void newFolders(ArrayList<FolderInfoHolder> newFolders)
        {
            android.os.Message msg = new android.os.Message();
            msg.obj = newFolders;
            msg.what = MSG_NEW_FOLDERS;
            sendMessage(msg);
        }

        public void workingAccount(int res)
        {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_WORKING_ACCOUNT;
            msg.arg1 = res;
            sendMessage(msg);
        }

        public void accountSizeChanged(long oldSize, long newSize)
        {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_ACCOUNT_SIZE_CHANGED;
            msg.obj = new Long[] { oldSize, newSize };
            sendMessage(msg);
        }

        public void folderLoading(String folder, boolean loading)
        {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_FOLDER_LOADING;
            msg.arg1 = loading ? 1 : 0;
            msg.obj = folder;
            sendMessage(msg);
        }

        public void progress(boolean progress)
        {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_PROGRESS;
            msg.arg1 = progress ? 1 : 0;
            sendMessage(msg);
        }

        public void dataChanged()
        {
            sendEmptyMessage(MSG_DATA_CHANGED);
        }

        public void folderSyncing(String folder)
        {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_FOLDER_SYNCING;
            msg.obj = new String[] { folder };
            sendMessage(msg);
        }

        public void sendingOutbox(boolean sending)
        {
            android.os.Message msg = new android.os.Message();
            msg.what = MSG_SENDING_OUTBOX;
            msg.arg1 = sending ? 1 : 0;
            sendMessage(msg);
        }
    }

    /**
    * This class is responsible for reloading the list of local messages for a
    * given folder, notifying the adapter that the message have been loaded and
    * queueing up a remote update of the folder.
     */

    private void checkMail(FolderInfoHolder folder)
    {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        final WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Email - UpdateWorker");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(Email.WAKE_LOCK_TIMEOUT);
        MessagingListener listener = new MessagingListener()
        {
            public void synchronizeMailboxFinished(Account account, String folder, int totalMessagesInMailbox, int numNewMessages)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }
                wakeLock.release();
            }

            @Override
            public void synchronizeMailboxFailed(Account account, String folder,
                                                 String message)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }
                wakeLock.release();
            }
        };
        MessagingController.getInstance(getApplication()).synchronizeMailbox(mAccount, folder.name, listener);
        sendMail(mAccount);
    }

    private static void actionHandleAccount(Context context, Account account, String initialFolder, boolean startup)
    {
        Intent intent = new Intent(context, FolderList.class);
        intent.putExtra(EXTRA_ACCOUNT, account);
        intent.putExtra(EXTRA_STARTUP, startup);

        if (initialFolder != null)
        {
            intent.putExtra(EXTRA_INITIAL_FOLDER, initialFolder);
        }

        context.startActivity(intent);
    }

    public static void actionHandleAccount(Context context, Account account, String initialFolder)
    {
        actionHandleAccount(context, account, initialFolder, false);
    }

    public static void actionHandleAccount(Context context, Account account, boolean startup)
    {
        actionHandleAccount(context, account, null, startup);
    }

    public static Intent actionHandleAccountIntent(Context context, Account account, String initialFolder)
    {
        Intent intent = new Intent(
            Intent.ACTION_VIEW,
            Uri.parse("email://accounts/" + account.getAccountNumber()),
            context,
            FolderList.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_ACCOUNT, account);
        intent.putExtra(EXTRA_CLEAR_NOTIFICATION, true);

        if (initialFolder != null)
        {
            intent.putExtra(EXTRA_INITIAL_FOLDER, initialFolder);
        }
        else
        {
            intent.putExtra(EXTRA_STARTUP, true);
        }
        return intent;
    }

    public static Intent actionHandleAccountIntent(Context context, Account account)
    {
        return actionHandleAccountIntent(context, account, null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {

        String initialFolder;

        super.onCreate(savedInstanceState);
        String savedFolderName = null;
        Intent intent = getIntent();
        mAccount = (Account)intent.getSerializableExtra(EXTRA_ACCOUNT);
        Log.v(Email.LOG_TAG, "savedInstanceState: " + (savedInstanceState==null));

        mDateFormat = android.text.format.DateFormat.getDateFormat(this);   // short format
        mTimeFormat = android.text.format.DateFormat.getTimeFormat(this);   // 12/24 date format

        if (savedInstanceState == null)
        {
            initialFolder = intent.getStringExtra(EXTRA_INITIAL_FOLDER);
            Log.v(Email.LOG_TAG, "EXTRA_INITIAL_FOLDER: " + initialFolder);
            mStartup = (boolean) intent.getBooleanExtra(EXTRA_STARTUP, false);
            Log.v(Email.LOG_TAG, "startup: " + mStartup);
            if (initialFolder == null
                    && mStartup)
            {
                initialFolder = mAccount.getAutoExpandFolderName();
            }
        }
        else
        {
            initialFolder = null;
            mStartup = false;
            savedFolderName = savedInstanceState.getString(STATE_CURRENT_FOLDER);
        }

        Log.v(Email.LOG_TAG, "mInitialFolder: " + initialFolder);
        if (initialFolder != null
                && !Email.FOLDER_NONE.equals(initialFolder))
        {
            onOpenFolder(initialFolder, true);
            finish();
        }
        else
        {
            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

            mListView = getListView();
            mListView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
            mListView.setLongClickable(true);
            mListView.setFastScrollEnabled(true);
            mListView.setScrollingCacheEnabled(true);
            mListView.setOnItemClickListener(new OnItemClickListener()
            {
                public void onItemClick(AdapterView parent, View v, int itemPosition, long id)
                {
                    Log.v(Email.LOG_TAG,"We're clicking "+itemPosition+" -- "+id);
                    MessageList.actionHandleFolder(FolderList.this, mAccount, ((FolderInfoHolder)mAdapter.getItem(id)).name, false);
                }
            });
            registerForContextMenu(mListView);

            /*
            * We manually save and restore the list's state because our adapter is
            * slow.
             */
            mListView.setSaveEnabled(false);

            mInflater = getLayoutInflater();

            mAdapter = new FolderListAdapter();

            final Object previousData = getLastNonConfigurationInstance();

            if (previousData != null)
            {
                //noinspection unchecked
                mAdapter.mFolders = (ArrayList<FolderInfoHolder>) previousData;
            }

            setListAdapter(mAdapter);

            setTitle(mAccount.getDescription());

            if (savedFolderName != null)
            {
                mSelectedContextFolder = mAdapter.getFolder(savedFolderName);
            }
        }
    }

    @Override public Object onRetainNonConfigurationInstance()
    {
        return mAdapter.mFolders;
    }

    @Override public void onPause()
    {
        super.onPause();
        MessagingController.getInstance(getApplication()).removeListener(mAdapter.mListener);
    }

    /**
    * On resume we refresh the folder list (in the background) and we refresh the
    * messages for any folder that is currently open. This guarantees that things
    * like unread message count and read status are updated.
     */
    @Override public void onResume()
    {
        super.onResume();

        MessagingController.getInstance(getApplication()).addListener(mAdapter.mListener);
        mAccount.refresh(Preferences.getPreferences(this));

        onRefresh(!REFRESH_REMOTE);

        NotificationManager notifMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notifMgr.cancel(mAccount.getAccountNumber());
        notifMgr.cancel(-1000 - mAccount.getAccountNumber());

    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        if (mSelectedContextFolder != null)
        {
            outState.putString(STATE_CURRENT_FOLDER, mSelectedContextFolder.name);
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        //Shortcuts that work no matter what is selected

        switch (keyCode)
        {
            case KeyEvent.KEYCODE_Q:
                //case KeyEvent.KEYCODE_BACK:
            {
                onAccounts();
                return true;
            }

            case KeyEvent.KEYCODE_S:
            {
                onEditAccount();
                return true;
            }

            case KeyEvent.KEYCODE_H:
            {
                Toast toast = Toast.makeText(this, R.string.message_list_help_key, Toast.LENGTH_LONG);
                toast.show();
                return true;
            }
        }//switch


        return super.onKeyDown(keyCode, event);
    }//onKeyDown

    private void onRefresh(final boolean forceRemote)
    {

        MessagingController.getInstance(getApplication()).listFolders(mAccount, forceRemote, mAdapter.mListener);

    }

    private void onEditAccount()
    {
        AccountSettings.actionSettings(this, mAccount);
    }

    private void onEditFolder(Account account, String folderName)
    {
        FolderSettings.actionSettings(this, account, folderName);
    }

    private void onAccounts()
    {
        if (mStartup || isTaskRoot())
        {
            Accounts.listAccounts(this);
        }

        finish();
    }

    private void onEmptyTrash(final Account account)
    {
        mHandler.dataChanged();

        MessagingListener listener = new MessagingListener()
        {
            @Override
            public void controllerCommandCompleted(boolean moreToDo)
            {
                Log.v(Email.LOG_TAG, "Empty Trash background task completed");
            }
        };

        MessagingController.getInstance(getApplication()).emptyTrash(account, listener);
    }

    private void checkMail(final Account account)
    {
        MessagingController.getInstance(getApplication()).checkMail(this, account, true, true, mAdapter.mListener);
    }

    private void sendMail(Account account)
    {
        MessagingController.getInstance(getApplication()).sendPendingMessages(account, mAdapter.mListener);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.compose:
                MessageCompose.actionCompose(this, mAccount);

                return true;

            case R.id.check_mail:
                checkMail(mAccount);

                return true;

            case R.id.send_messages:
                Log.i(Email.LOG_TAG, "sending pending messages");

                MessagingController.getInstance(getApplication()).sendPendingMessages(mAccount, null);
                return true;
            case R.id.accounts:
                onAccounts();

                return true;

            case R.id.list_folders:
                onRefresh(REFRESH_REMOTE);

                return true;

            case R.id.account_settings:
                onEditAccount();

                return true;

            case R.id.empty_trash:
                onEmptyTrash(mAccount);

                return true;

            case R.id.compact:
                onCompact(mAccount);

                return true;

            case R.id.clear:
                onClear(mAccount);

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onOpenFolder(String folder, boolean startup)
    {
        MessageList.actionHandleFolder(this, mAccount, folder, startup);
    }

    private void onCompact(Account account)
    {
        mHandler.workingAccount(R.string.compacting_account);
        MessagingController.getInstance(getApplication()).compact(account, null);
    }

    private void onClear(Account account)
    {
        mHandler.workingAccount(R.string.clearing_account);
        MessagingController.getInstance(getApplication()).clear(account, null);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.folder_list_option, menu);
        return true;
    }

    @Override public boolean onContextItemSelected(MenuItem item)
    {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item .getMenuInfo();
        FolderInfoHolder folder = (FolderInfoHolder) mAdapter.getItem(info.position);

        switch (item.getItemId())
        {
            case R.id.open_folder:
                onOpenFolder(folder.name, false);
                break;

            case R.id.mark_all_as_read:
                onMarkAllAsRead(mAccount, folder.name);
                break;

            case R.id.send_messages:
                Log.i(Email.LOG_TAG, "sending pending messages from " + folder.name);
                sendMail(mAccount);

                break;

            case R.id.check_mail:
                Log.i(Email.LOG_TAG, "refresh folder " + folder.name);
                checkMail(folder);

                break;

            case R.id.folder_settings:
                Log.i(Email.LOG_TAG, "edit folder settings for " + folder.name);

                onEditFolder(mAccount, folder.name);

                break;

            case R.id.empty_trash:
                Log.i(Email.LOG_TAG, "empty trash");

                onEmptyTrash(mAccount);

                break;
        }

        return super.onContextItemSelected(item);
    }

    private FolderInfoHolder mSelectedContextFolder = null;


    private void onMarkAllAsRead(final Account account, final String folder)
    {
        mSelectedContextFolder = mAdapter.getFolder(folder);
        showDialog(DIALOG_MARK_ALL_AS_READ);
    }


    @Override
    public Dialog onCreateDialog(int id)
    {
        switch (id)
        {
            case DIALOG_MARK_ALL_AS_READ:
                return createMarkAllAsReadDialog();
        }

        return super.onCreateDialog(id);
    }

    public void onPrepareDialog(int id, Dialog dialog)
    {
        switch (id)
        {
            case DIALOG_MARK_ALL_AS_READ:
                ((AlertDialog)dialog).setMessage(getString(R.string.mark_all_as_read_dlg_instructions_fmt,
                                                 mSelectedContextFolder.displayName));

                break;

            default:
                super.onPrepareDialog(id, dialog);
        }
    }

    private Dialog createMarkAllAsReadDialog()
    {
        return new AlertDialog.Builder(this)
               .setTitle(R.string.mark_all_as_read_dlg_title)
               .setMessage(getString(R.string.mark_all_as_read_dlg_instructions_fmt,
                                     mSelectedContextFolder.displayName))
               .setPositiveButton(R.string.okay_action, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                dismissDialog(DIALOG_MARK_ALL_AS_READ);

                try
                {

                    MessagingController.getInstance(getApplication()).markAllMessagesRead(mAccount, mSelectedContextFolder.name);

                    mSelectedContextFolder.unreadMessageCount = 0;

                    mHandler.dataChanged();


                }
                catch (Exception e)
                {
                    // Ignore
                }
            }
        })

               .setNegativeButton(R.string.cancel_action, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                dismissDialog(DIALOG_MARK_ALL_AS_READ);
            }
        })

               .create();
    }


    @Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        getMenuInflater().inflate(R.menu.folder_context, menu);

        FolderInfoHolder folder = (FolderInfoHolder) mAdapter.getItem(info.position);

        menu.setHeaderTitle((CharSequence) folder.displayName);

        if (!folder.name.equals(mAccount.getTrashFolderName()))
            menu.findItem(R.id.empty_trash).setVisible(false);

        if (folder.outbox)
        {
            menu.findItem(R.id.check_mail).setVisible(false);
        }
        else
        {
            menu.findItem(R.id.send_messages).setVisible(false);
        }

        menu.setHeaderTitle(folder.displayName);
    }

    private String truncateStatus(String mess)
    {
        if (mess != null && mess.length() > 27)
        {
            mess = mess.substring(0, 27);
        }

        return mess;
    }

    class FolderListAdapter extends BaseAdapter
    {
        private ArrayList<FolderInfoHolder> mFolders = new ArrayList<FolderInfoHolder>();

        public Object getItem(long position)
        {
            return getItem((int)position);
        }

        public Object getItem(int position)
        {
            return mFolders.get(position);
        }


        public long getItemId(int position)
        {
            return position ;
        }

        public int getCount()
        {
            return mFolders.size();
        }

        public boolean isEnabled(int item)
        {
            return true;
        }

        public boolean areAllItemsEnabled()
        {
            return true;
        }

        private MessagingListener mListener = new MessagingListener()
        {

            @Override
            public void listFoldersStarted(Account account)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }

                mHandler.progress(true);
            }

            @Override
            public void listFoldersFailed(Account account, String message)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }

                mHandler.progress(false);

                if (Config.LOGV)
                {
                    Log.v(Email.LOG_TAG, "listFoldersFailed " + message);
                }
            }

            @Override
            public void listFoldersFinished(Account account)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }

                mHandler.progress(false);
                MessagingController.getInstance(getApplication()).refreshListener(mAdapter.mListener);
                mHandler.dataChanged();

            }

            @Override
            public void listFolders(Account account, Folder[] folders)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }

                ArrayList<FolderInfoHolder> newFolders = new ArrayList<FolderInfoHolder>();

                Account.FolderMode aMode = account.getFolderDisplayMode();

                for (Folder folder : folders)
                {
                    try
                    {
                        folder.refresh(Preferences.getPreferences(getApplication().getApplicationContext()));

                        Folder.FolderClass fMode = folder.getDisplayClass();

                        if ((aMode == Account.FolderMode.FIRST_CLASS && fMode != Folder.FolderClass.FIRST_CLASS)
                                || (aMode == Account.FolderMode.FIRST_AND_SECOND_CLASS &&
                                    fMode != Folder.FolderClass.FIRST_CLASS &&
                                    fMode != Folder.FolderClass.SECOND_CLASS)
                                || (aMode == Account.FolderMode.NOT_SECOND_CLASS && fMode == Folder.FolderClass.SECOND_CLASS))
                        {
                            continue;
                        }
                    }
                    catch (MessagingException me)
                    {
                        Log.e(Email.LOG_TAG, "Couldn't get prefs to check for displayability of folder " + folder.getName(), me);
                    }

                    FolderInfoHolder holder = null;

                    int folderIndex = getFolderIndex(folder.getName());
                    if (folderIndex >= 0)
                    {
                        holder = (FolderInfoHolder) getItem(folderIndex);
                    }

                    if (holder == null)
                    {
                        holder = new FolderInfoHolder(folder);
                    }
                    else
                    {
                        holder.populate(folder);

                    }

                    newFolders.add(holder);
                }
                Collections.sort(newFolders);
                mHandler.newFolders(newFolders);

            }

            public void synchronizeMailboxStarted(Account account, String folder)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }

                mHandler.progress(true);
                mHandler.folderLoading(folder, true);
                mHandler.folderSyncing(folder);
                mHandler.dataChanged();
            }

            @Override
            public void synchronizeMailboxFinished(Account account, String folder, int totalMessagesInMailbox, int numNewMessages)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }
                mHandler.progress(false);
                mHandler.folderLoading(folder, false);
                // mHandler.folderStatus(folder, null);
                mHandler.folderSyncing(null);

                refreshFolder(account, folder);

            }

            private void refreshFolder(Account account, String folderName)
            {
                // There has to be a cheaper way to get at the localFolder object than this
                try
                {
                    if (account != null && folderName != null)
                    {
                        Folder localFolder = (Folder) Store.getInstance(account.getLocalStoreUri(), getApplication()).getFolder(folderName);
                        if (localFolder != null)
                        {
                            FolderInfoHolder folderHolder = getFolder(folderName);
                            if (folderHolder != null)
                            {
                                folderHolder.populate(localFolder);
                                mHandler.dataChanged();
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    Log.e(Email.LOG_TAG, "Exception while populating folder", e);
                }

            }

            @Override
            public void synchronizeMailboxFailed(Account account, String folder,
                                                 String message)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }


                mHandler.progress(false);

                mHandler.folderLoading(folder, false);

                //   String mess = truncateStatus(message);

                //   mHandler.folderStatus(folder, mess);
                FolderInfoHolder holder = getFolder(folder);

                if (holder != null)
                {
                    holder.lastChecked = 0;
                }

                mHandler.folderSyncing(null);

                mHandler.dataChanged();
            }

            @Override
            public void setPushActive(Account account, String folderName, boolean enabled)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }
                FolderInfoHolder holder = getFolder(folderName);

                if (holder != null)
                {
                    holder.pushActive = enabled;

                    mHandler.dataChanged();
                }
            }


            @Override
            public void messageDeleted(Account account,
                                       String folder, Message message)
            {
                synchronizeMailboxRemovedMessage(account,
                                                 folder, message);
            }

            @Override
            public void emptyTrashCompleted(Account account)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }
                refreshFolder(account, mAccount.getTrashFolderName());
            }

            @Override
            public void folderStatusChanged(Account account, String folderName)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }
                refreshFolder(account, folderName);
            }

            @Override
            public void sendPendingMessagesCompleted(Account account)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }

                mHandler.sendingOutbox(false);
                refreshFolder(account, mAccount.getOutboxFolderName());
            }

            @Override
            public void sendPendingMessagesStarted(Account account)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }

                mHandler.sendingOutbox(true);

                mHandler.dataChanged();
            }

            @Override
            public void sendPendingMessagesFailed(Account account)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }

                mHandler.sendingOutbox(false);
                refreshFolder(account, mAccount.getOutboxFolderName());
            }

            public void accountSizeChanged(Account account, long oldSize, long newSize)
            {
                if (!account.equals(mAccount))
                {
                    return;
                }

                mHandler.accountSizeChanged(oldSize, newSize);

            }

        };


        public int getFolderIndex(String folder)
        {
            FolderInfoHolder searchHolder = new FolderInfoHolder();
            searchHolder.name = folder;
            return   mFolders.indexOf((Object) searchHolder);
        }

        public FolderInfoHolder getFolder(String folder)
        {
            FolderInfoHolder holder = null;

            int index = getFolderIndex(folder);
            if (index >= 0)
            {
                holder = (FolderInfoHolder) getItem(index);
                if (holder != null)
                {
                    return holder;
                }
            }
            return null;
        }

        public View getView(int position, View convertView, ViewGroup parent)
        {
            if (position <= getCount())
            {
                return  getItemView(position, convertView, parent);
            }
            else
            {
                // XXX TODO - should catch an exception here
                return null;
            }
        }

        public View getItemView(int itemPosition, View convertView, ViewGroup parent)
        {
            FolderInfoHolder folder = (FolderInfoHolder) getItem(itemPosition);
            View view;
            if ((convertView != null) && (convertView.getId() == R.layout.folder_list_item))
            {
                view = convertView;
            }
            else
            {
                view = mInflater.inflate(R.layout.folder_list_item, parent, false);
                view.setId(R.layout.folder_list_item);
            }


            FolderViewHolder holder = (FolderViewHolder) view.getTag();

            if (holder == null)
            {
                holder = new FolderViewHolder();
                holder.folderName = (TextView) view.findViewById(R.id.folder_name);
                holder.newMessageCount = (TextView) view.findViewById(R.id.folder_unread_message_count);
                holder.folderStatus = (TextView) view.findViewById(R.id.folder_status);
                holder.rawFolderName = folder.name;

                view.setTag(holder);
            }

            if (folder == null)
            {
                return view;
            }

            holder.folderName.setText(folder.displayName);
            String statusText = "";

            if (folder.loading)
            {
                statusText = getString(R.string.status_loading);
            }
            else if (folder.status != null)
            {
                statusText = folder.status;
            }
            else if (folder.lastChecked != 0)
            {
                Date lastCheckedDate = new Date(folder.lastChecked);

                statusText = mTimeFormat.format(lastCheckedDate) + " "+
                             mDateFormat.format(lastCheckedDate);
            }

            if (folder.pushActive)
            {
                statusText = getString(R.string.folder_push_active_symbol) + " "+ statusText;
            }

            if (statusText != null)
            {
                holder.folderStatus.setText(statusText);
                holder.folderStatus.setVisibility(View.VISIBLE);
            }
            else
            {
                holder.folderStatus.setText(null);
                holder.folderStatus.setVisibility(View.GONE);
            }

            if (folder.unreadMessageCount != 0)
            {
                holder.newMessageCount.setText(Integer
                                               .toString(folder.unreadMessageCount));
                holder.newMessageCount.setVisibility(View.VISIBLE);
            }
            else
            {
                holder.newMessageCount.setVisibility(View.GONE);
            }

            return view;
        }

        public boolean hasStableIds()
        {
            return false;
        }

        public boolean isItemSelectable(int position)
        {
            return true;
        }

    }

    public class FolderInfoHolder implements Comparable<FolderInfoHolder>
    {
        public String name;

        public String displayName;

        public long lastChecked;

        public int unreadMessageCount;

        public boolean loading;

        public String status;

        public boolean pushActive;

        public boolean lastCheckFailed;

        /**
         * Outbox is handled differently from any other folder.
         */
        public boolean outbox;


        public boolean equals(Object o)
        {
            if (this.name.equals(((FolderInfoHolder)o).name))
            {
                return true;
            }
            else
            {
                return false;
            }
        }

        public int compareTo(FolderInfoHolder o)
        {
            String s1 = this.name;
            String s2 = o.name;

            if (Email.INBOX.equalsIgnoreCase(s1) && Email.INBOX.equalsIgnoreCase(s2))
            {
                return 0;
            }
            else if (Email.INBOX.equalsIgnoreCase(s1))
            {
                return -1;
            }
            else if (Email.INBOX.equalsIgnoreCase(s2))
            {
                return 1;
            }
            else
            {
                int ret = s1.compareToIgnoreCase(s2);
                if (ret != 0)
                {
                    return ret;
                }
                else
                {
                    return s1.compareTo(s2);
                }
            }

        }

        // constructor for an empty object for comparisons
        public FolderInfoHolder()
        {
        }

        public FolderInfoHolder(Folder folder)
        {
            populate(folder);
        }
        public void populate(Folder folder)
        {
            int unreadCount = 0;

            try
            {
                folder.open(Folder.OpenMode.READ_WRITE);
                unreadCount = folder.getUnreadMessageCount();
            }
            catch (MessagingException me)
            {
                Log.e(Email.LOG_TAG, "Folder.getUnreadMessageCount() failed", me);
            }

            this.name = folder.getName();

            if (this.name.equalsIgnoreCase(Email.INBOX))
            {
                this.displayName = getString(R.string.special_mailbox_name_inbox);
            }
            else
            {
                this.displayName = folder.getName();
            }

            if (this.name.equals(mAccount.getOutboxFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_outbox_fmt), this.name);
                this.outbox = true;
            }

            if (this.name.equals(mAccount.getDraftsFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_drafts_fmt), this.name);
            }

            if (this.name.equals(mAccount.getTrashFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_trash_fmt), this.name);
            }

            if (this.name.equals(mAccount.getSentFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_sent_fmt), this.name);
            }

            this.lastChecked = folder.getLastUpdate();

            String mess = truncateStatus(folder.getStatus());

            this.status = mess;

            this.unreadMessageCount = unreadCount;

            try
            {
                folder.close(false);
            }
            catch (MessagingException me)
            {
                Log.e(Email.LOG_TAG, "Folder.close() failed", me);
            }
        }
    }

    class FolderViewHolder
    {
        public TextView folderName;

        public TextView folderStatus;

        public TextView newMessageCount;

        public String rawFolderName;
    }

}
