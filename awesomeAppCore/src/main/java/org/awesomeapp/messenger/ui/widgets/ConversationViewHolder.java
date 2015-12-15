package org.awesomeapp.messenger.ui.widgets;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import info.guardianproject.otr.app.im.R;

/**
 * Created by n8fr8 on 12/14/15.
 */
public class ConversationViewHolder
{

    public TextView mLine1;
    public TextView mLine2;
    public TextView mStatusText;
    public ImageView mAvatar;
    public ImageView mStatusIcon;
    public View mContainer;
    public ImageView mMediaThumb;

    public ConversationViewHolder(View view)
    {
        mLine1 = (TextView) view.findViewById(R.id.line1);
        mLine2 = (TextView) view.findViewById(R.id.line2);

        mAvatar = (ImageView)view.findViewById(R.id.avatar);
        mStatusIcon = (ImageView)view.findViewById(R.id.statusIcon);
        mStatusText = (TextView)view.findViewById(R.id.statusText);
        //holder.mEncryptionIcon = (ImageView)view.findViewById(R.id.encryptionIcon);

        mContainer = view.findViewById(R.id.message_container);

        mMediaThumb = (ImageView)view.findViewById(R.id.media_thumbnail);
    }

}
