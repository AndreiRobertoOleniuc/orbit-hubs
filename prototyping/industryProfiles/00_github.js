async function fetchGitHubRepos(username) {
    const response = await fetch(
        `https://api.github.com/users/${username}/repos?sort=updated&per_page=10`,
        {
            headers: {
                Accept: "application/vnd.github.mercy-preview+json",
            },
        }
    );

    const repos = await response.json();
    const oneYearAgo = new Date();
    oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);

    const extracted = repos
        .filter((repo) => new Date(repo.updated_at) > oneYearAgo)
        .map((repo) => ({
            name: repo.name,
            language: repo.language,
            description: repo.description,
            topics: repo.topics ?? [],
        }));

    console.log(extracted);
}

//fetchGitHubRepos("AndreiRobertoOleniuc");
//fetchGitHubRepos("marvinvr");
//fetchGitHubRepos("nicolasstevenyates");
fetchGitHubRepos("Luca-FH");