package org.wikipedia.commons

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.dialog_image_preview.*
import kotlinx.android.synthetic.main.fragment_file_page.*
import kotlinx.android.synthetic.main.fragment_file_page.errorView
import kotlinx.android.synthetic.main.fragment_file_page.filePageView
import kotlinx.android.synthetic.main.fragment_file_page.progressBar
import org.apache.commons.lang3.StringUtils
import org.wikipedia.R
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.mwapi.MwQueryResponse
import org.wikipedia.dataclient.mwapi.media.MediaHelper.getImageCaptions
import org.wikipedia.page.PageTitle
import org.wikipedia.suggestededits.SuggestedEditsSummary
import org.wikipedia.util.L10nUtil
import org.wikipedia.util.StringUtil
import org.wikipedia.util.log.L

class FilePageFragment : Fragment() {
    private lateinit var pageTitle: PageTitle
    private lateinit var suggestedEditsSummary: SuggestedEditsSummary
    private lateinit var imageTags: Map<String, List<String>>
    private var isFromCommons: Boolean = true
    private var thumbnailWidth: Int = 0
    private var thumbnailHeight: Int = 0
    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageTitle = arguments?.getParcelable(ARG_PAGE_TITLE)!!
        retainInstance = true
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        L10nUtil.setConditionalLayoutDirection(container, pageTitle.wikiSite.languageCode())
        return inflater.inflate(R.layout.fragment_file_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = false
            loadImageInfo()
        }
        errorView.setBackClickListener { requireActivity().finish() }
        loadImageInfo()
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    private fun showError(caught: Throwable?) {
        progressBar.visibility = View.GONE
        filePageView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorView.setError(caught)
    }

    private fun loadImageInfo() {
        errorView.visibility = View.GONE
        filePageView.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        disposables.add(Observable.zip(getImageCaptions(pageTitle.prefixedText),
                ServiceFactory.get(pageTitle.wikiSite).getImageInfo(pageTitle.prefixedText, pageTitle.wikiSite.languageCode()),
                BiFunction<Map<String, String>, MwQueryResponse, Pair<Map<String, String>, MwQueryResponse>> {
                    caption: Map<String, String>, page: MwQueryResponse -> Pair(caption, page)
                })
                .subscribeOn(Schedulers.io())
                .flatMap {
                    val page = it.second.query()!!.pages()!![0]
                    val imageInfo = page.imageInfo()
                    imageInfo!!.captions = it.first
                    suggestedEditsSummary =  SuggestedEditsSummary(
                            pageTitle.prefixedText,
                            pageTitle.wikiSite.languageCode(),
                            pageTitle,
                            pageTitle.displayText,
                            StringUtils.defaultIfBlank(StringUtil.fromHtml(imageInfo.metadata!!.imageDescription()).toString(), null),
                            imageInfo.thumbUrl,
                            null,
                            imageInfo.timestamp,
                            imageInfo.user,
                            imageInfo.metadata
                    )
                    isFromCommons = page.isImageFromCommons
                    thumbnailHeight = imageInfo.thumbHeight
                    thumbnailWidth = imageInfo.thumbWidth
                    ImageTagsProvider.getImageTagsObservable(page.pageId(), suggestedEditsSummary.lang)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doAfterTerminate {
                    filePageView.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    filePageView.setup(
                            suggestedEditsSummary,
                            imageTags,
                            dialogDetailContainer.width,
                            thumbnailWidth, thumbnailHeight,
                            imageFromCommons = isFromCommons,
                            showFilename = false,
                            showEditButton = false
                    )
                }
                .subscribe({ imageTags = it }, { caught ->
                    L.e(caught)
                    showError(caught)
                }))
    }

    companion object {
        private const val ARG_PAGE_TITLE = "pageTitle"
        fun newInstance(pageTitle: PageTitle): FilePageFragment {
            val fragment = FilePageFragment()
            val args = Bundle()
            args.putParcelable(ARG_PAGE_TITLE, pageTitle)
            fragment.arguments = args
            return fragment
        }
    }
}
