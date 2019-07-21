# Contributing

**First, thanks for your interest in contributing to QuPath!** 😄

There are lots of ways to do so - and most of them don't even involve writing any code.

* [Code of conduct](#code-of-conduct)
* [I just want to ask a question!](#i-just-want-to-ask-a-question)
* [How can I contribute?](#how-can-i-contribute)
  * [Citing QuPath](#citing-qupath)
  * [Reporting bugs](#reporting-bugs)
  * [Supporting others](#supporting-others)
  * [Suggesting enhancements](#suggesting-enhancements)
  * [Working together](#working-together)
  * [Writing code](#writing-code)


## Code of conduct

This project is governed by its [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you're expected to uphold this code.


## I just want to ask a question!

For questions and discussions, use the forum at [forum.image.sc](https://forum.image.sc/tags/qupath).

You should also check out [Getting help](https://github.com/qupath/qupath/wiki/Getting-help) - maybe there's already an answer out there.


## How can I contribute?

### Citing QuPath

If you use QuPath in your research, please **cite the QuPath paper** published in _Scientific Reports_:

**Bankhead, P. et al. (2017). QuPath: Open source software for digital pathology image analysis. _Scientific Reports_.**
https://doi.org/10.1038/s41598-017-17204-5

There are details on how to cite QuPath [here](https://github.com/qupath/qupath/wiki/Citing-QuPath) along with a list of papers where it is used. Include a citation to make sure your papers have a 📗 and not a 😞

> Writing and supporting usable, open source software takes _a lot_ of time. [Many papers don't properly cite the software they use](https://doi.org/10.1038/s41592-019-0350-x), even though doing so is one of the simplest ways to help the developers get support to continue their work.
>
> So whenever you use any software for research, please remember to check how it should be cited!



### Reporting bugs

If you find a bug, you can report it [here](https://github.com/qupath/qupath/issues).

Please do follow the template to make the job of finding and fixing the bug as painless as possible.

### Supporting others

#### Answering questions
[forum.image.sc](https://forum.image.sc/tags/qupath) isn't just a good place for asking questions - it's a good place for answering them too.

It's _enormously helpful_ when users of the software answer one another's questions rather than leaving it up to the developer... and it's a good way to learn as well.

> The best answer often doesn't depend just on knowing the software, but also understanding the application... so please do consider answering questions, even if you feel your expertise isn't in QuPath.

#### Creating documentation
Figured out how to do something, and want to spare others the time it took?

Tutorials, tweetorials, blog posts and videos can all help add to the community documentation. If you're on Twitter, be sure to [@QuPath](https://twitter.com/qupath).


### Suggesting enhancements

The [forum](https://forum.image.sc/tags/qupath) is also a good place to suggest enhancements to QuPath.

Particularly worthy suggestions may be added as [issues](https://github.com/qupath/qupath/issues) - but please be aware that there is a _long_ backlog of enhancement planned already.

The main limiting factor is time. Which leads on to...

### Working together

QuPath is being developed at the University of Edinburgh.
One way to contribute is to [join the group](https://www.vacancies.ed.ac.uk/pls/corehrrecruit/erq_jobspec_version_4.jobspec?p_id=048500).

Another is to collaborate on research projects that result in developing new methods that can be integrated back into QuPath for everyone to use.

Contact Pete [here](https://www.ed.ac.uk/pathology/people/staff-students/peter-bankhead) if you wish to talk academic things.

### Writing code

#### Scripts & extensions

The easiest way to extend QuPath is to write a script or an extension.

There's some documentation at https://github.com/qupath/qupath/wiki/Writing-custom-scripts

However, be warned that the API remains quite unstable. Changing this is a high priority, but do expect breaking changes to continue while major new features are added over the next few releases.

> Most API changes are made in the interests of simplification and laying the foundations for major new features (like the pixel classifier). Improving and extending the software is currently a higher priority than backwards compatibility... but hopefully it will reach a stage where both can be achieved.
>
> In the meantime, if you're missing a method and can't track down where it has gone, ask on the [forum](https://forum.image.sc/tags/qupath).

#### Pull requests

You can also contribute by submitting pull requests to QuPath itself.

Because of the API-is-unstable situation, it would be _much appreciated_ if you discuss any proposed changes first on the [forum](https://forum.image.sc/tags/qupath) or by [opening an issue](https://github.com/qupath/qupath/issues). It may be that someone is already working on what you'd like to change, or it might not be compatible with some other work-in-progress.

In accordance with [GitHub's Terms of Service](https://help.github.com/en/articles/github-terms-of-service#6-contributions-under-repository-license), any contributions you _do_ make are under the [same license as QuPath](LICENSE.md). Please make sure you have the rights for any code that you contribute, and you attribute any dependencies appropriately.
