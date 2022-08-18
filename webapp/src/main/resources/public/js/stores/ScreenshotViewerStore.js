class ScreenshotViewerStore {

    constructor() {
        this.setDefaultState();
    }

    setDefaultState() {
        this.show = false;
        this.src = null;
        this.number = 0;
        this.total = 0;
        this.branchStatisticScreenshots = [];
        this.textUnits = [];
        this.isDeleting = false
    }

    openScreenshotsViewer(branchStatisticsScreenshots) {
        this.branchStatisticScreenshots = branchStatisticsScreenshots;
        this.loadScreenshot(1);
        this.show = true;
    }

    loadScreenshot(number) {
        if (this.branchStatisticScreenshots.length > 0) {
            this.number = number;
            this.total = this.branchStatisticScreenshots.length;
            let branchStatisticScreenshot = this.branchStatisticScreenshots[this.number - 1];
            this.src = branchStatisticScreenshot.src;
            this.textUnits = branchStatisticScreenshot.textUnits;
        }
    }

    closeScreenshotsViewer() {
        this.setDefaultState();
    }

    goToPrevious() {
        if (this.number > 1) {
            this.loadScreenshot(this.number - 1);
        }
    }

    goToNext() {
        if (this.number < this.total) {
            this.loadScreenshot(this.number + 1);
        }
    }

    delete() {
        this.isDeleting = true;
        this.getInstance().delete()
    }

    onDeleteScreenshotSuccess() {
        this.branchStatisticScreenshots.splice(this.number - 1, 1)

        if (this.branchStatisticScreenshots.length) {
            this.loadScreenshot(1)
        } else {
            this.closeScreenshotsViewer()
        }
        this.isDeleting = false
        console.log("ScreenshotViewerStore::onDeleteScreenshotSuccess");
    }

    onDeleteScreenshotFailure() {
        this.isDeleting = false
        console.log("ScreenshotViewerStore::onDeleteScreenshotFailure");
    }
}

export default ScreenshotViewerStore;
